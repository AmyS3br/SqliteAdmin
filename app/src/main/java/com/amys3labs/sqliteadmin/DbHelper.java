package com.amys3labs.sqliteadmin;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DbHelper {

    private final String dbPath;
    private SQLiteDatabase db;

    public DbHelper(String dbPath) {
        this.dbPath = dbPath;
        open();
    }

    public void open() {
        if (db == null || !db.isOpen()) {
            db = SQLiteDatabase.openDatabase(dbPath, null,
                    SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY);
        }
    }

    public void close() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    public SQLiteDatabase getDb() {
        open();
        return db;
    }

    public String getPath() {
        return dbPath;
    }

    public String getDatabaseInfo(Context ctx) {
        File f = new File(dbPath);
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getString(R.string.info_path, dbPath)).append("\n");
        sb.append(ctx.getString(R.string.info_size, f.length() / 1024.0)).append("\n");
        try {
            Cursor c = db.rawQuery("PRAGMA encoding", null);
            if (c.moveToFirst()) sb.append(ctx.getString(R.string.info_encoding, c.getString(0))).append("\n");
            c.close();
            c = db.rawQuery("PRAGMA page_size", null);
            if (c.moveToFirst()) sb.append(ctx.getString(R.string.info_page_size, c.getInt(0))).append("\n");
            c.close();
            c = db.rawQuery("PRAGMA user_version", null);
            if (c.moveToFirst()) sb.append(ctx.getString(R.string.info_user_version, c.getInt(0))).append("\n");
            c.close();
            c = db.rawQuery("PRAGMA journal_mode", null);
            if (c.moveToFirst()) sb.append(ctx.getString(R.string.info_journal, c.getString(0))).append("\n");
            c.close();
            List<String> tables = getTables();
            sb.append(ctx.getString(R.string.info_tables, tables.size()));
        } catch (Exception ignored) {}
        return sb.toString();
    }

    public List<String> getTables() {
        List<String> list = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'"
                        + " AND name NOT LIKE 'sqlite_%'"
                        + " AND name NOT LIKE 'android_%'"
                        + " ORDER BY name",
                null);
        while (c.moveToNext()) {
            list.add(c.getString(0));
        }
        c.close();
        return list;
    }

    public void dropTable(String table) {
        db.execSQL("DROP TABLE IF EXISTS " + quote(table));
    }

    public void renameTable(String oldName, String newName) {
        db.execSQL("ALTER TABLE " + quote(oldName) + " RENAME TO " + quote(newName));
    }

    public void createTable(String tableName, List<ColumnDef> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column required");
        }
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(quote(tableName)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(columns.get(i).toSqlFragment());
        }
        sql.append(")");
        db.execSQL(sql.toString());
    }

    public static class ColumnInfo {
        public String name;
        public String type;
        public boolean notNull;
        public String defaultValue;
        public boolean primaryKey;
        public boolean unique;
        public int cid;
    }

    public List<ColumnInfo> getColumns(String table) {
        List<ColumnInfo> list = new ArrayList<>();
        Cursor c = db.rawQuery("PRAGMA table_info(" + quote(table) + ")", null);
        while (c.moveToNext()) {
            ColumnInfo col = new ColumnInfo();
            col.cid = c.getInt(0);
            col.name = c.getString(1);
            col.type = c.getString(2);
            col.notNull = c.getInt(3) == 1;
            col.defaultValue = c.getString(4);
            col.primaryKey = c.getInt(5) > 0;
            list.add(col);
        }
        c.close();

        // UNIQUE is not in table_info – detect via single-column unique indexes
        java.util.Set<String> uniqueCols = getSingleColumnUniqueNames(table);
        for (ColumnInfo col : list) {
            if (uniqueCols.contains(col.name)) {
                col.unique = true;
            }
            // PRIMARY KEY implies unique
            if (col.primaryKey) {
                col.unique = true;
            }
        }
        return list;
    }

    /** Names of columns that have a single-column UNIQUE index (or UNIQUE column constraint). */
    private java.util.Set<String> getSingleColumnUniqueNames(String table) {
        java.util.Set<String> result = new java.util.HashSet<>();
        Cursor idx = null;
        try {
            idx = db.rawQuery("PRAGMA index_list(" + quote(table) + ")", null);
            while (idx.moveToNext()) {
                // columns: seq, name, unique, origin, partial
                int unique = idx.getInt(2);
                if (unique != 1) continue;
                String indexName = idx.getString(1);
                Cursor info = null;
                try {
                    info = db.rawQuery("PRAGMA index_info(" + quote(indexName) + ")", null);
                    java.util.List<String> cols = new java.util.ArrayList<>();
                    while (info.moveToNext()) {
                        // seqno, cid, name
                        cols.add(info.getString(2));
                    }
                    if (cols.size() == 1 && cols.get(0) != null) {
                        result.add(cols.get(0));
                    }
                } finally {
                    if (info != null) info.close();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (idx != null) idx.close();
        }
        return result;
    }

    public Cursor getTableData(String table, int limit, int offset) {
        return getTableData(table, limit, offset, null, true, null);
    }

    /**
     * @param orderByColumn column name or null
     * @param ascending sort direction
     * @param filter if non-null/non-empty, applies WHERE across all columns with LIKE
     */
    public Cursor getTableData(String table, int limit, int offset,
                               String orderByColumn, boolean ascending, String filter) {
        StringBuilder sql = new StringBuilder("SELECT rowid AS __rowid__, * FROM ");
        sql.append(quote(table));
        String[] filterArgs = null;
        if (filter != null && !filter.trim().isEmpty()) {
            List<ColumnInfo> cols = getColumns(table);
            StringBuilder where = new StringBuilder();
            List<String> args = new ArrayList<>();
            String pattern = "%" + filter.trim() + "%";
            for (ColumnInfo c : cols) {
                if (where.length() > 0) where.append(" OR ");
                where.append("CAST(").append(quote(c.name)).append(" AS TEXT) LIKE ?");
                args.add(pattern);
            }
            if (where.length() > 0) {
                sql.append(" WHERE ").append(where);
                filterArgs = args.toArray(new String[0]);
            }
        }
        if (orderByColumn != null && !orderByColumn.isEmpty()) {
            sql.append(" ORDER BY ").append(quote(orderByColumn));
            sql.append(ascending ? " ASC" : " DESC");
        }
        sql.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);
        return db.rawQuery(sql.toString(), filterArgs);
    }

    public long getRowCountFiltered(String table, String filter) {
        if (filter == null || filter.trim().isEmpty()) return getRowCount(table);
        List<ColumnInfo> cols = getColumns(table);
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        String pattern = "%" + filter.trim() + "%";
        for (ColumnInfo c : cols) {
            if (where.length() > 0) where.append(" OR ");
            where.append("CAST(").append(quote(c.name)).append(" AS TEXT) LIKE ?");
            args.add(pattern);
        }
        if (where.length() == 0) return getRowCount(table);
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + quote(table) + " WHERE " + where,
                args.toArray(new String[0]));
        long count = 0;
        if (c.moveToFirst()) count = c.getLong(0);
        c.close();
        return count;
    }

    public long getRowCount(String table) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + quote(table), null);
        long count = 0;
        if (c.moveToFirst()) count = c.getLong(0);
        c.close();
        return count;
    }

    public long insertRow(String table, ContentValues values) {
        // nullColumnHack required when values is empty (all columns use DEFAULT)
        String nullHack = null;
        if (values == null || values.size() == 0) {
            List<ColumnInfo> cols = getColumns(table);
            if (!cols.isEmpty()) nullHack = cols.get(0).name;
        }
        // insertOrThrow surfaces UNIQUE / NOT NULL / etc. as SQLiteConstraintException
        return db.insertOrThrow(table, nullHack, values);
    }

    public int updateRow(String table, ContentValues values, long rowid) {
        int n = db.update(table, values, "rowid=?", new String[]{String.valueOf(rowid)});
        if (n < 0) {
            throw new android.database.SQLException("Update failed");
        }
        return n;
    }

    public int deleteRow(String table, long rowid) {
        return db.delete(table, "rowid=?", new String[]{String.valueOf(rowid)});
    }

    public static class ColumnDef {
        public String name;
        public String type = "TEXT";
        public boolean primaryKey;
        public boolean autoIncrement;
        public boolean notNull;
        public boolean unique;
        public String defaultValue;

        public ColumnDef(String name) {
            this.name = name;
        }

        /** Build the column definition fragment for CREATE / ADD COLUMN. */
        public String toSqlFragment() {
            StringBuilder sb = new StringBuilder();
            sb.append(quote(name)).append(" ").append(type == null || type.isEmpty() ? "TEXT" : type);
            if (primaryKey) {
                sb.append(" PRIMARY KEY");
                // AUTOINCREMENT is only valid with INTEGER PRIMARY KEY
                if (autoIncrement && type != null && type.toUpperCase().contains("INT")) {
                    sb.append(" AUTOINCREMENT");
                }
            }
            if (notNull) sb.append(" NOT NULL");
            if (unique && !primaryKey) sb.append(" UNIQUE");
            if (defaultValue != null && !defaultValue.trim().isEmpty()) {
                String dv = defaultValue.trim();
                // Quote if it looks like a string and isn't already quoted / a function
                if (!dv.startsWith("'") && !dv.startsWith("\"")
                        && !dv.equalsIgnoreCase("NULL")
                        && !dv.equalsIgnoreCase("CURRENT_TIMESTAMP")
                        && !dv.equalsIgnoreCase("CURRENT_DATE")
                        && !dv.equalsIgnoreCase("CURRENT_TIME")
                        && !isNumeric(dv)) {
                    sb.append(" DEFAULT '").append(dv.replace("'", "''")).append("'");
                } else {
                    sb.append(" DEFAULT ").append(dv);
                }
            }
            return sb.toString();
        }

        private static boolean isNumeric(String s) {
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static String quote(String name) {
            return "\"" + name.replace("\"", "\"\"") + "\"";
        }
    }

    public void addColumn(String table, ColumnDef col) {
        // SQLite forbids PRIMARY KEY / UNIQUE on ALTER TABLE ADD COLUMN.
        // Also AUTOINCREMENT cannot be added this way. Rebuild when needed.
        if (col.primaryKey || col.unique || col.autoIncrement) {
            rebuildTableAddColumn(table, col);
            return;
        }
        StringBuilder sql = new StringBuilder("ALTER TABLE ");
        sql.append(quote(table)).append(" ADD COLUMN ");
        sql.append(col.toSqlFragment());
        db.execSQL(sql.toString());
    }

    private void rebuildTableAddColumn(String table, ColumnDef newCol) {
        List<ColumnInfo> cols = getColumns(table);
        List<String> colNames = new ArrayList<>();
        StringBuilder create = new StringBuilder("CREATE TABLE \"__tmp__\" (");
        for (int i = 0; i < cols.size(); i++) {
            ColumnInfo c = cols.get(i);
            if (i > 0) create.append(", ");
            create.append(columnInfoToSql(c));
            colNames.add(c.name);
        }
        if (!cols.isEmpty()) create.append(", ");
        create.append(newCol.toSqlFragment());
        create.append(")");
        db.execSQL(create.toString());

        if (!colNames.isEmpty()) {
            StringBuilder copy = new StringBuilder("INSERT INTO \"__tmp__\" (");
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0) copy.append(", ");
                copy.append(quote(colNames.get(i)));
            }
            copy.append(") SELECT ");
            for (int i = 0; i < colNames.size(); i++) {
                if (i > 0) copy.append(", ");
                copy.append(quote(colNames.get(i)));
            }
            copy.append(" FROM ").append(quote(table));
            db.execSQL(copy.toString());
        }

        db.execSQL("DROP TABLE " + quote(table));
        db.execSQL("ALTER TABLE \"__tmp__\" RENAME TO " + quote(table));
    }

    /** Rebuild column definition SQL from ColumnInfo, preserving UNIQUE. */
    private String columnInfoToSql(ColumnInfo c) {
        StringBuilder sb = new StringBuilder();
        sb.append(quote(c.name)).append(" ").append(c.type == null || c.type.isEmpty() ? "TEXT" : c.type);
        if (c.primaryKey) sb.append(" PRIMARY KEY");
        if (c.notNull && !c.primaryKey) sb.append(" NOT NULL");
        if (c.unique && !c.primaryKey) sb.append(" UNIQUE");
        if (c.defaultValue != null && !c.defaultValue.isEmpty()) {
            String dv = c.defaultValue.trim();
            // table_info returns defaults already (sometimes quoted)
            sb.append(" DEFAULT ").append(dv);
        }
        return sb.toString();
    }

    public void renameColumn(String table, String oldName, String newName) {
        try {
            db.execSQL("ALTER TABLE " + quote(table) + " RENAME COLUMN " +
                    quote(oldName) + " TO " + quote(newName));
        } catch (SQLiteException e) {
            rebuildTableRenameColumn(table, oldName, newName);
        }
    }

    public void dropColumn(String table, String columnName) {
        try {
            db.execSQL("ALTER TABLE " + quote(table) + " DROP COLUMN " + quote(columnName));
        } catch (SQLiteException e) {
            rebuildTableDropColumn(table, columnName);
        }
    }

    public void modifyColumn(String table, String columnName, ColumnDef newDef) {
        rebuildTableModifyColumn(table, columnName, newDef);
    }

    private void rebuildTableRenameColumn(String table, String oldName, String newName) {
        List<ColumnInfo> cols = getColumns(table);
        List<String> colNames = new ArrayList<>();
        StringBuilder create = new StringBuilder("CREATE TABLE \"__tmp__\" (");
        for (int i = 0; i < cols.size(); i++) {
            ColumnInfo c = cols.get(i);
            if (i > 0) create.append(", ");
            ColumnInfo copy = new ColumnInfo();
            copy.name = c.name.equals(oldName) ? newName : c.name;
            copy.type = c.type;
            copy.primaryKey = c.primaryKey;
            copy.notNull = c.notNull;
            copy.unique = c.unique;
            copy.defaultValue = c.defaultValue;
            create.append(columnInfoToSql(copy));
            colNames.add(c.name);
        }
        create.append(")");
        db.execSQL(create.toString());

        StringBuilder copySql = new StringBuilder("INSERT INTO \"__tmp__\" SELECT ");
        for (int i = 0; i < colNames.size(); i++) {
            if (i > 0) copySql.append(", ");
            copySql.append(quote(colNames.get(i)));
        }
        copySql.append(" FROM ").append(quote(table));
        db.execSQL(copySql.toString());

        db.execSQL("DROP TABLE " + quote(table));
        db.execSQL("ALTER TABLE \"__tmp__\" RENAME TO " + quote(table));
    }

    private void rebuildTableDropColumn(String table, String dropName) {
        List<ColumnInfo> cols = getColumns(table);
        List<String> keep = new ArrayList<>();
        StringBuilder create = new StringBuilder("CREATE TABLE \"__tmp__\" (");
        boolean first = true;
        for (ColumnInfo c : cols) {
            if (c.name.equals(dropName)) continue;
            if (!first) create.append(", ");
            first = false;
            create.append(columnInfoToSql(c));
            keep.add(c.name);
        }
        create.append(")");
        db.execSQL(create.toString());

        if (!keep.isEmpty()) {
            StringBuilder copy = new StringBuilder("INSERT INTO \"__tmp__\" SELECT ");
            for (int i = 0; i < keep.size(); i++) {
                if (i > 0) copy.append(", ");
                copy.append(quote(keep.get(i)));
            }
            copy.append(" FROM ").append(quote(table));
            db.execSQL(copy.toString());
        }

        db.execSQL("DROP TABLE " + quote(table));
        db.execSQL("ALTER TABLE \"__tmp__\" RENAME TO " + quote(table));
    }

    private void rebuildTableModifyColumn(String table, String columnName, ColumnDef newDef) {
        List<ColumnInfo> cols = getColumns(table);
        List<String> colNames = new ArrayList<>();
        StringBuilder create = new StringBuilder("CREATE TABLE \"__tmp__\" (");
        for (int i = 0; i < cols.size(); i++) {
            ColumnInfo c = cols.get(i);
            if (i > 0) create.append(", ");
            if (c.name.equals(columnName)) {
                create.append(newDef.toSqlFragment());
            } else {
                create.append(columnInfoToSql(c));
            }
            colNames.add(c.name);
        }
        create.append(")");
        db.execSQL(create.toString());

        // Map old column name -> new if renamed
        StringBuilder copy = new StringBuilder("INSERT INTO \"__tmp__\" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) copy.append(", ");
            if (cols.get(i).name.equals(columnName)) {
                copy.append(quote(newDef.name));
            } else {
                copy.append(quote(cols.get(i).name));
            }
        }
        copy.append(") SELECT ");
        for (int i = 0; i < colNames.size(); i++) {
            if (i > 0) copy.append(", ");
            copy.append(quote(colNames.get(i)));
        }
        copy.append(" FROM ").append(quote(table));
        db.execSQL(copy.toString());

        db.execSQL("DROP TABLE " + quote(table));
        db.execSQL("ALTER TABLE \"__tmp__\" RENAME TO " + quote(table));
    }

    public Cursor rawQuery(String sql) {
        return db.rawQuery(sql, null);
    }

    public void execSQL(String sql) {
        db.execSQL(sql);
    }

    /** Force a checkpoint so the main .db file contains all data (important before saving). */
    public void checkpoint() {
        try {
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
        } catch (Exception ignored) {}
    }


    // ── Maintenance ────────────────────────────────────────────────────────

    public void vacuum() {
        db.execSQL("VACUUM");
    }

    public void analyze() {
        db.execSQL("ANALYZE");
    }

    public String integrityCheck() {
        Cursor c = db.rawQuery("PRAGMA integrity_check", null);
        StringBuilder sb = new StringBuilder();
        while (c.moveToNext()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(c.getString(0));
        }
        c.close();
        return sb.toString();
    }

    public boolean foreignKeysEnabled() {
        Cursor c = db.rawQuery("PRAGMA foreign_keys", null);
        boolean on = false;
        if (c.moveToFirst()) on = c.getInt(0) == 1;
        c.close();
        return on;
    }

    public void setForeignKeys(boolean enabled) {
        db.execSQL("PRAGMA foreign_keys=" + (enabled ? "ON" : "OFF"));
    }

    public String getForeignKeyInfo(String table) {
        Cursor c = db.rawQuery("PRAGMA foreign_key_list(" + quote(table) + ")", null);
        StringBuilder sb = new StringBuilder();
        while (c.moveToNext()) {
            // id, seq, table, from, to, on_update, on_delete, match
            sb.append(c.getString(3)).append(" → ")
              .append(c.getString(2)).append("(").append(c.getString(4)).append(")")
              .append("  ON UPDATE ").append(c.getString(5))
              .append("  ON DELETE ").append(c.getString(6))
              .append("\n");
        }
        c.close();
        if (sb.length() == 0) return "(none)";
        return sb.toString();
    }

    // ── Indexes ────────────────────────────────────────────────────────────

    public static class IndexInfo {
        public String name;
        public boolean unique;
        public String origin; // c = create index, u = unique, pk = primary key
        public List<String> columns = new ArrayList<>();
    }

    public List<IndexInfo> getIndexes(String table) {
        List<IndexInfo> list = new ArrayList<>();
        Cursor idx = db.rawQuery("PRAGMA index_list(" + quote(table) + ")", null);
        try {
            while (idx.moveToNext()) {
                IndexInfo info = new IndexInfo();
                info.name = idx.getString(1);
                info.unique = idx.getInt(2) == 1;
                if (idx.getColumnCount() > 3) info.origin = idx.getString(3);
                Cursor infoC = db.rawQuery("PRAGMA index_info(" + quote(info.name) + ")", null);
                try {
                    while (infoC.moveToNext()) {
                        info.columns.add(infoC.getString(2));
                    }
                } finally {
                    infoC.close();
                }
                list.add(info);
            }
        } finally {
            idx.close();
        }
        return list;
    }

    public void createIndex(String table, String indexName, List<String> columns, boolean unique) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("At least one column required");
        }
        StringBuilder sql = new StringBuilder();
        sql.append(unique ? "CREATE UNIQUE INDEX " : "CREATE INDEX ");
        sql.append(quote(indexName)).append(" ON ").append(quote(table)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(columns.get(i)));
        }
        sql.append(")");
        db.execSQL(sql.toString());
    }

    public void dropIndex(String indexName) {
        db.execSQL("DROP INDEX IF EXISTS " + quote(indexName));
    }

    /** Export table as JSON array of objects. */
    public String exportTableToJson(String table) {
        List<ColumnInfo> cols = getColumns(table);
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        Cursor c = db.rawQuery("SELECT * FROM " + quote(table), null);
        try {
            boolean first = true;
            while (c.moveToNext()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("  {");
                for (int i = 0; i < c.getColumnCount(); i++) {
                    if (i > 0) sb.append(", ");
                    String name = c.getColumnName(i);
                    sb.append(jsonEscape(name)).append(": ");
                    if (c.isNull(i)) {
                        sb.append("null");
                    } else {
                        int t = c.getType(i);
                        if (t == Cursor.FIELD_TYPE_INTEGER) {
                            sb.append(c.getLong(i));
                        } else if (t == Cursor.FIELD_TYPE_FLOAT) {
                            sb.append(c.getDouble(i));
                        } else {
                            sb.append(jsonEscape(c.getString(i)));
                        }
                    }
                }
                sb.append("}");
            }
        } finally {
            c.close();
        }
        sb.append("\n]");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ── Export / Import ────────────────────────────────────────────────────

    /** Export a single table to CSV text (header + rows). */
    public String exportTableToCsv(String table) {
        List<ColumnInfo> cols = getColumns(table);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(csvEscape(cols.get(i).name));
        }
        sb.append("\n");

        Cursor c = db.rawQuery("SELECT * FROM " + quote(table), null);
        try {
            while (c.moveToNext()) {
                for (int i = 0; i < c.getColumnCount(); i++) {
                    if (i > 0) sb.append(",");
                    if (c.isNull(i)) {
                        // empty field for NULL
                    } else {
                        sb.append(csvEscape(c.getString(i)));
                    }
                }
                sb.append("\n");
            }
        } finally {
            c.close();
        }
        return sb.toString();
    }

    /** Export entire database as SQL dump (CREATE + INSERT). */
    public String exportSqlDump() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- SQLite Admin dump\n");
        sb.append("-- Path: ").append(dbPath).append("\n\n");
        sb.append("-- Run in SQL console (transaction managed by the app)\n\n");

        Cursor master = db.rawQuery(
                "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL"
                        + " AND name NOT LIKE 'sqlite_%'"
                        + " AND name NOT LIKE 'android_%'"
                        + " ORDER BY type DESC, name",
                null);
        try {
            while (master.moveToNext()) {
                String type = master.getString(0);
                String name = master.getString(1);
                String sql = master.getString(2);
                // Safer re-import: CREATE TABLE → CREATE TABLE IF NOT EXISTS
                if ("table".equals(type) && sql != null) {
                    String upper = sql.toUpperCase();
                    if (upper.startsWith("CREATE TABLE") && !upper.startsWith("CREATE TABLE IF NOT EXISTS")) {
                        sql = "CREATE TABLE IF NOT EXISTS" + sql.substring("CREATE TABLE".length());
                    }
                }
                if ("index".equals(type) && sql != null) {
                    String upper = sql.toUpperCase();
                    if (upper.startsWith("CREATE ") && !upper.contains("IF NOT EXISTS")) {
                        if (upper.startsWith("CREATE UNIQUE INDEX")) {
                            sql = "CREATE UNIQUE INDEX IF NOT EXISTS" + sql.substring("CREATE UNIQUE INDEX".length());
                        } else if (upper.startsWith("CREATE INDEX")) {
                            sql = "CREATE INDEX IF NOT EXISTS" + sql.substring("CREATE INDEX".length());
                        }
                    }
                }
                sb.append(sql).append(";\n\n");

                if ("table".equals(type)) {
                    Cursor data = db.rawQuery("SELECT * FROM " + quote(name), null);
                    try {
                        String[] colnames = data.getColumnNames();
                        while (data.moveToNext()) {
                            sb.append("INSERT INTO ").append(quote(name)).append(" (");
                            for (int i = 0; i < colnames.length; i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(quote(colnames[i]));
                            }
                            sb.append(") VALUES (");
                            for (int i = 0; i < colnames.length; i++) {
                                if (i > 0) sb.append(", ");
                                if (data.isNull(i)) {
                                    sb.append("NULL");
                                } else {
                                    int typeAffinity = data.getType(i);
                                    if (typeAffinity == Cursor.FIELD_TYPE_INTEGER) {
                                        sb.append(data.getLong(i));
                                    } else if (typeAffinity == Cursor.FIELD_TYPE_FLOAT) {
                                        sb.append(data.getDouble(i));
                                    } else if (typeAffinity == Cursor.FIELD_TYPE_BLOB) {
                                        sb.append("X'").append(bytesToHex(data.getBlob(i))).append("'");
                                    } else {
                                        sb.append("'").append(data.getString(i).replace("'", "''")).append("'");
                                    }
                                }
                            }
                            sb.append(");\n");
                        }
                        sb.append("\n");
                    } finally {
                        data.close();
                    }
                }
            }
        } finally {
            master.close();
        }
        sb.append("-- end of dump\n");
        return sb.toString();
    }

    /**
     * Import CSV text into a table.
     * First line = headers. Creates table if createIfMissing and table does not exist.
     * Returns number of rows inserted.
     */
    public int importCsv(String table, String csv, boolean createIfMissing) {
        if (csv == null || csv.trim().isEmpty()) return 0;
        String[] lines = csv.split("\r?\n");
        if (lines.length == 0) return 0;

        List<String> headers = parseCsvLine(lines[0]);
        if (headers.isEmpty()) return 0;

        List<String> existing = getTables();
        boolean exists = existing.contains(table);
        if (!exists) {
            if (!createIfMissing) {
                throw new IllegalArgumentException("Table does not exist: " + table);
            }
            List<ColumnDef> cols = new ArrayList<>();
            for (String h : headers) {
                ColumnDef d = new ColumnDef(h.isEmpty() ? "col" : h);
                d.type = "TEXT";
                cols.add(d);
            }
            createTable(table, cols);
        }

        int inserted = 0;
        db.beginTransaction();
        try {
            for (int li = 1; li < lines.length; li++) {
                String line = lines[li].trim();
                if (line.isEmpty()) continue;
                List<String> values = parseCsvLine(lines[li]);
                ContentValues cv = new ContentValues();
                for (int i = 0; i < headers.size(); i++) {
                    String col = headers.get(i);
                    if (col.isEmpty()) continue;
                    String val = i < values.size() ? values.get(i) : null;
                    if (val == null || val.isEmpty()) {
                        cv.putNull(col);
                    } else {
                        cv.put(col, val);
                    }
                }
                if (db.insert(table, null, cv) != -1) inserted++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    result.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        result.add(cur.toString());
        return result;
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    public void attachDatabase(String alias, String path) {
        open();
        db.execSQL("ATTACH DATABASE " + quote(path) + " AS " + quote(alias));
    }

    public void detachDatabase(String alias) {
        open();
        db.execSQL("DETACH DATABASE " + quote(alias));
    }

    public String getPragma(String name) {
        open();
        Cursor c = db.rawQuery("PRAGMA " + name, null);
        try {
            if (c.moveToFirst()) {
                return c.isNull(0) ? "NULL" : c.getString(0);
            }
        } finally {
            c.close();
        }
        return "";
    }

    public void setPragma(String name, String value) {
        open();
        // value may be identifier or number; pass carefully
        db.execSQL("PRAGMA " + name + " = " + value);
    }

    /** Schema suggestions for autocomplete: tables + table.column */
    public java.util.List<String> getSchemaSuggestions() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String table : getTables()) {
            out.add(table);
            try {
                for (ColumnInfo col : getColumns(table)) {
                    out.add(table + "." + col.name);
                    if (!out.contains(col.name)) {
                        out.add(col.name);
                    }
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

}
