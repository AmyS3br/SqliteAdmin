package com.amys3labs.sqliteadmin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        try {
            super.attachBaseContext(LocaleHelper.apply(newBase));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    private String dbPath;
    private String tableName;
    private DbHelper helper;
    private AppPrefs prefs;

    private RecyclerView recycler;
    private TextView tvRowCount;
    private TextView tvPage;
    private EditText etFilter;
    private RowAdapter adapter;

    private final List<Map<String, String>> rows = new ArrayList<>();
    private final List<Long> rowids = new ArrayList<>();
    private List<DbHelper.ColumnInfo> columns = new ArrayList<>();

    private int pageSize = 100;
    private int currentOffset = 0;
    private String filterText = null;
    private String orderByColumn = null;
    private boolean orderAsc = true;
    private boolean modified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);

        dbPath = getIntent().getStringExtra("db_path");
        tableName = getIntent().getStringExtra("table_name");
        if (dbPath == null || tableName == null) {
            finish();
            return;
        }

        prefs = new AppPrefs(this);
        pageSize = prefs.getPageSize();
        helper = new DbHelper(dbPath);
        columns = helper.getColumns(tableName);

        TextView tvTitle = findViewById(R.id.tvTableTitle);
        tvTitle.setText(tableName);
        tvRowCount = findViewById(R.id.tvRowCount);
        tvPage = findViewById(R.id.tvPage);
        etFilter = findViewById(R.id.etFilter);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnAddRow).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showEditDialog(-1, null); }
        });
        findViewById(R.id.btnSchema).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSchemaDialog(); }
        });
        findViewById(R.id.btnIndexes).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showIndexesDialog(); }
        });
        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadData(); }
        });
        findViewById(R.id.btnFilter).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyFilter(); }
        });
        findViewById(R.id.btnSort).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortDialog(); }
        });
        findViewById(R.id.btnPrev).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                currentOffset = Math.max(0, currentOffset - pageSize);
                loadData();
            }
        });
        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                currentOffset += pageSize;
                loadData();
            }
        });

        etFilter.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    applyFilter();
                    return true;
                }
                return false;
            }
        });

        recycler = findViewById(R.id.recyclerRows);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RowAdapter();
        recycler.setAdapter(adapter);

        loadData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (helper != null) helper.close();
    }

    private void markModified() {
        modified = true;
        setResult(RESULT_OK);
    }

    private void applyFilter() {
        String f = etFilter.getText().toString().trim();
        filterText = f.isEmpty() ? null : f;
        currentOffset = 0;
        loadData();
    }

    private void showSortDialog() {
        final String[] names = new String[columns.size() + 1];
        names[0] = getString(R.string.sort_none);
        for (int i = 0; i < columns.size(); i++) names[i + 1] = columns.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sort_by_column))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            orderByColumn = null;
                            loadData();
                            return;
                        }
                        final String col = names[which];
                        new AlertDialog.Builder(TableActivity.this)
                                .setTitle(col)
                                .setItems(new String[]{getString(R.string.ascending), "Descending"},
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                orderByColumn = col;
                                                orderAsc = (w == 0);
                                                currentOffset = 0;
                                                loadData();
                                            }
                                        })
                                .show();
                    }
                })
                .show();
    }

    private void loadData() {
        rows.clear();
        rowids.clear();
        pageSize = prefs.getPageSize();

        long total = helper.getRowCountFiltered(tableName, filterText);
        if (currentOffset >= total && total > 0) {
            currentOffset = (int) ((total - 1) / pageSize) * pageSize;
        }
        if (currentOffset < 0) currentOffset = 0;

        int page = pageSize > 0 ? (currentOffset / pageSize) + 1 : 1;
        int pages = pageSize > 0 ? (int) Math.max(1, (total + pageSize - 1) / pageSize) : 1;
        tvRowCount.setText(getString(R.string.rows_count, total)
                + (filterText != null ? getString(R.string.rows_filtered) : "")
                + (orderByColumn != null
                        ? getString(R.string.sort_prefix, orderByColumn + (orderAsc ? "↑" : "↓"))
                        : ""));
        tvPage.setText(page + " / " + pages);

        Cursor c = helper.getTableData(tableName, pageSize, currentOffset,
                orderByColumn, orderAsc, filterText);
        try {
            String[] names = c.getColumnNames();
            while (c.moveToNext()) {
                long rowid = c.getLong(0);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 1; i < names.length; i++) {
                    if (c.isNull(i)) {
                        map.put(names[i], "NULL");
                    } else if (c.getType(i) == Cursor.FIELD_TYPE_BLOB) {
                        byte[] blob = c.getBlob(i);
                        int len = blob == null ? 0 : blob.length;
                        map.put(names[i], "BLOB:" + len + ":" + bytesToHexLimited(blob, 256));
                    } else {
                        map.put(names[i], c.getString(i));
                    }
                }
                rows.add(map);
                rowids.add(rowid);
            }
        } finally {
            c.close();
        }
        adapter.notifyDataSetChanged();
    }


    /** Turn SQL-ish presets into concrete values for ContentValues inserts. */
    private String resolveRowValue(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return t;
        String u = t.toUpperCase();
        java.text.SimpleDateFormat dts =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        java.text.SimpleDateFormat ds =
                new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        java.text.SimpleDateFormat ts =
                new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US);
        java.util.Date now = new java.util.Date();
        if (u.equals("CURRENT_TIMESTAMP")
                || u.equals("DATETIME('NOW')")
                || u.equals("DATETIME('NOW', 'LOCALTIME')")) {
            return dts.format(now);
        }
        if (u.equals("CURRENT_DATE") || u.equals("DATE('NOW')")
                || u.equals("DATE('NOW', 'LOCALTIME')")) {
            return ds.format(now);
        }
        if (u.equals("CURRENT_TIME") || u.equals("TIME('NOW')")
                || u.equals("TIME('NOW', 'LOCALTIME')")) {
            return ts.format(now);
        }
        if (u.equals("NULL")) return null;
        return t;
    }

    private void showEditDialog(final int position, final Map<String, String> existing) {
        final boolean isNew = position < 0;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 24, 40, 16);

        if (!isNew) {
            TextView rid = new TextView(this);
            rid.setText(getString(R.string.rowid_label, String.valueOf(rowids.get(position))));
            rid.setTextSize(12);
            rid.setPadding(0, 0, 0, 8);
            root.addView(rid);
        }

        final Map<String, EditText> editors = new LinkedHashMap<>();
        final Map<String, CheckBox> useDefaultBoxes = new LinkedHashMap<>();

        for (final DbHelper.ColumnInfo col : columns) {
            TextView label = new TextView(this);
            StringBuilder lbl = new StringBuilder(col.name).append("  (").append(col.type).append(")");
            if (col.primaryKey) lbl.append("  PK");
            else if (col.unique) lbl.append("  UNIQUE");
            if (col.notNull) lbl.append("  NOT NULL");
            if (col.defaultValue != null && !col.defaultValue.isEmpty()) {
                lbl.append("  DEFAULT ").append(col.defaultValue);
            }
            label.setText(lbl.toString());
            label.setTextSize(13);
            label.setPadding(0, 8, 0, 0);
            root.addView(label);

            final boolean hasDefault = isNew && col.defaultValue != null && !col.defaultValue.isEmpty();
            final EditText et = new EditText(this);
            et.setSingleLine(true);
            et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

            if (existing != null && existing.containsKey(col.name)) {
                String val = existing.get(col.name);
                if (!"NULL".equals(val) && val != null) {
                    if (val.startsWith("BLOB:")) {
                        try {
                            int len = Integer.parseInt(val.split(":", 3)[1]);
                            et.setText(getString(R.string.blob_preview, len));
                        } catch (Exception ignored) {
                            et.setText("BLOB");
                        }
                        final String blobVal = val;
                        et.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                maybeShowBlobPreview(col.name, blobVal);
                                return true;
                            }
                        });
                    } else {
                        et.setText(val);
                    }
                }
            }

            // Value presets dropdown (same idea as column DEFAULT presets)
            final Spinner spVal = new Spinner(this);
            String[] presets = DatabaseActivity.defaultPresetsForType(col.type);
            // For row values, map "(none)" to empty and keep useful shortcuts
            spVal.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, presets));
            spVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            spVal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                private boolean first = true;
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (first) { first = false; return; }
                    if (!et.isEnabled()) return; // respect "use default"
                    String preset = (String) parent.getItemAtPosition(position);
                    if ("(none)".equals(preset)) {
                        et.setText("");
                    } else if ("''".equals(preset)) {
                        et.setText(""); // empty string value for TEXT
                    } else if ("NULL".equals(preset)) {
                        et.setText("");
                        et.setHint("NULL");
                    } else {
                        // Strip outer quotes for literal string presets like 'N/A'
                        if (preset.length() >= 2 && preset.startsWith("'") && preset.endsWith("'")) {
                            et.setText(preset.substring(1, preset.length() - 1));
                        } else {
                            et.setText(preset);
                        }
                    }
                    et.setSelection(et.getText().length());
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            if (hasDefault) {
                final CheckBox cb = new CheckBox(this);
                cb.setText(getString(R.string.use_default, col.defaultValue));
                cb.setChecked(true);
                et.setText(col.defaultValue);
                et.setEnabled(false);
                spVal.setEnabled(false);
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            et.setText(col.defaultValue);
                            et.setEnabled(false);
                            spVal.setEnabled(false);
                            spVal.setSelection(0);
                        } else {
                            et.setEnabled(true);
                            spVal.setEnabled(true);
                            et.setText("");
                            et.setHint(getString(R.string.hint_custom));
                        }
                    }
                });
                root.addView(cb);
                useDefaultBoxes.put(col.name, cb);
            } else if (isNew) {
                if (col.primaryKey && col.type != null && col.type.toUpperCase().contains("INT")) {
                    et.setHint(getString(R.string.hint_auto));
                } else {
                    et.setHint(getString(R.string.hint_value_preset));
                }
            }

            LinearLayout valRow = new LinearLayout(this);
            valRow.setOrientation(LinearLayout.HORIZONTAL);
            valRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            valRow.addView(spVal);
            valRow.addView(et);
            root.addView(valRow);

            editors.put(col.name, et);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);

        new AlertDialog.Builder(this)
                .setTitle(isNew ? getString(R.string.add_row_title) : getString(R.string.edit_row_title))
                .setView(scroll)
                .setPositiveButton(getString(R.string.save), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ContentValues cv = new ContentValues();
                        for (Map.Entry<String, EditText> e : editors.entrySet()) {
                            String colName = e.getKey();
                            EditText et = e.getValue();
                            CheckBox useDef = useDefaultBoxes.get(colName);

                            DbHelper.ColumnInfo colInfo = null;
                            for (DbHelper.ColumnInfo ci : columns) {
                                if (ci.name.equals(colName)) {
                                    colInfo = ci;
                                    break;
                                }
                            }

                            if (isNew && useDef != null && useDef.isChecked()) {
                                // Omit column → SQLite applies DEFAULT
                                continue;
                            }

                            String v = et.getText().toString();
                            if (v.isEmpty()) {
                                if (isNew) {
                                    boolean isPk = colInfo != null && colInfo.primaryKey;
                                    boolean hasDef = colInfo != null
                                            && colInfo.defaultValue != null
                                            && !colInfo.defaultValue.isEmpty();
                                    if (!hasDef && !isPk) {
                                        cv.putNull(colName);
                                    }
                                } else {
                                    cv.putNull(colName);
                                }
                            } else {
                                String resolved = resolveRowValue(v);
                                if (resolved == null) {
                                    cv.putNull(colName);
                                } else {
                                    cv.put(colName, resolved);
                                }
                            }
                        }
                        try {
                            if (isNew) {
                                long id = helper.insertRow(tableName, cv);
                                if (id == -1) {
                                    throw new Exception(getString(R.string.insert_failed));
                                }
                            } else {
                                helper.updateRow(tableName, cv, rowids.get(position));
                            }
                            markModified();
                            loadData();
                            Toast.makeText(TableActivity.this, getString(R.string.row_saved), Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {
                            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                            if (msg.toLowerCase().contains("unique") || msg.contains("SQLITE_CONSTRAINT")) {
                                msg = getString(R.string.constraint_error, msg);
                            }
                            new AlertDialog.Builder(TableActivity.this)
                                    .setTitle(getString(R.string.save_failed_title))
                                    .setMessage(msg)
                                    .setPositiveButton(getString(R.string.ok), null)
                                    .show();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showSchemaDialog() {
        columns = helper.getColumns(tableName);
        StringBuilder sb = new StringBuilder();
        for (DbHelper.ColumnInfo c : columns) {
            sb.append(c.name).append("  ").append(c.type);
            if (c.primaryKey) sb.append("  PRIMARY KEY");
            if (c.unique && !c.primaryKey) sb.append("  UNIQUE");
            if (c.notNull) sb.append("  NOT NULL");
            if (c.defaultValue != null) sb.append("  DEFAULT ").append(c.defaultValue);
            sb.append("\n");
        }
        sb.append("\n").append(getString(R.string.foreign_keys)).append("\n");
        sb.append(helper.getForeignKeyInfo(tableName));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.schema_title, tableName))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.alter_table), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAlterMenu();
                    }
                })
                .show();
    }

    private void showIndexesDialog() {
        List<DbHelper.IndexInfo> indexes = helper.getIndexes(tableName);
        StringBuilder sb = new StringBuilder();
        if (indexes.isEmpty()) {
            sb.append(getString(R.string.no_indexes)).append("\n");
        } else {
            for (DbHelper.IndexInfo ix : indexes) {
                sb.append(ix.name);
                if (ix.unique) sb.append("  UNIQUE");
                if (ix.origin != null) sb.append("  [").append(ix.origin).append("]");
                sb.append("\n  columns: ").append(ix.columns).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.indexes_title, tableName))
                .setMessage(sb.toString())
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.create_index), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showCreateIndexDialog();
                    }
                })
                .setNegativeButton(getString(R.string.drop_index), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDropIndexDialog();
                    }
                })
                .show();
    }

    private void showCreateIndexDialog() {
        final EditText etName = new EditText(this);
        etName.setHint(getString(R.string.index_name));
        etName.setSingleLine(true);

        final String[] colNames = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) colNames[i] = columns.get(i).name;
        final boolean[] checked = new boolean[columns.size()];
        final CheckBox cbUnique = new CheckBox(this);
        cbUnique.setText(getString(R.string.unique_index));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 16, 40, 16);
        root.addView(etName);
        root.addView(cbUnique);
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.tap_columns));
        hint.setPadding(0, 12, 0, 4);
        root.addView(hint);
        final LinearLayout colBox = new LinearLayout(this);
        colBox.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < colNames.length; i++) {
            final int idx = i;
            final CheckBox cb = new CheckBox(this);
            cb.setText(colNames[i]);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    checked[idx] = isChecked;
                }
            });
            colBox.addView(cb);
        }
        root.addView(colBox);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.create_index))
                .setView(scroll)
                .setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(TableActivity.this, getString(R.string.name_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<String> cols = new ArrayList<>();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) cols.add(colNames[i]);
                        }
                        if (cols.isEmpty()) {
                            Toast.makeText(TableActivity.this, getString(R.string.select_one_column), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            helper.createIndex(tableName, name, cols, cbUnique.isChecked());
                            markModified();
                            Toast.makeText(TableActivity.this, getString(R.string.index_created), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showDropIndexDialog() {
        List<DbHelper.IndexInfo> indexes = helper.getIndexes(tableName);
        final List<DbHelper.IndexInfo> droppable = new ArrayList<>();
        for (DbHelper.IndexInfo ix : indexes) {
            // Skip auto PK indexes if origin is pk
            if ("pk".equals(ix.origin)) continue;
            droppable.add(ix);
        }
        if (droppable.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_droppable_indexes), Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = new String[droppable.size()];
        for (int i = 0; i < droppable.size(); i++) names[i] = droppable.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.drop_which_index))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            helper.dropIndex(names[which]);
                            markModified();
                            Toast.makeText(TableActivity.this, getString(R.string.dropped), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private void showAlterMenu() {
        String[] items = {getString(R.string.add_column_title), getString(R.string.rename_column), getString(R.string.drop_column), getString(R.string.modify_column)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alter_table))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) showAddColumnDialog();
                        else if (which == 1) showRenameColumnDialog();
                        else if (which == 2) showDropColumnDialog();
                        else if (which == 3) showModifyColumnDialog();
                    }
                })
                .show();
    }

    private void showAddColumnDialog() {
        final LinearLayout root = buildColumnEditor("", "TEXT", false, false, false, false, null);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_column_title))
                .setView(scroll)
                .setPositiveButton(getString(R.string.add_row), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DbHelper.ColumnDef def = readColumnEditor(root);
                        if (def == null || def.name.isEmpty()) return;
                        try {
                            helper.addColumn(tableName, def);
                            markModified();
                            columns = helper.getColumns(tableName);
                            Toast.makeText(TableActivity.this, getString(R.string.column_added), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showRenameColumnDialog() {
        final String[] names = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) names[i] = columns.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rename_which_column))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String oldName = names[which];
                        final EditText et = new EditText(TableActivity.this);
                        et.setText(oldName);
                        et.setSingleLine(true);
                        new AlertDialog.Builder(TableActivity.this)
                                .setTitle(getString(R.string.new_name))
                                .setView(et)
                                .setPositiveButton(getString(R.string.rename), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        String newName = et.getText().toString().trim();
                                        if (newName.isEmpty()) return;
                                        try {
                                            helper.renameColumn(tableName, oldName, newName);
                                            markModified();
                                            columns = helper.getColumns(tableName);
                                            loadData();
                                            Toast.makeText(TableActivity.this, getString(R.string.renamed), Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                })
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                })
                .show();
    }

    private void showDropColumnDialog() {
        final String[] names = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) names[i] = columns.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.drop_which_column))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String col = names[which];
                        new AlertDialog.Builder(TableActivity.this)
                                .setTitle(getString(R.string.confirm))
                                .setMessage(getString(R.string.drop_column_msg, col))
                                .setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        try {
                                            helper.dropColumn(tableName, col);
                                            markModified();
                                            columns = helper.getColumns(tableName);
                                            loadData();
                                            Toast.makeText(TableActivity.this, getString(R.string.column_dropped), Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                })
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                })
                .show();
    }

    private void showModifyColumnDialog() {
        final String[] names = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) names[i] = columns.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.modify_which_column))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final DbHelper.ColumnInfo old = columns.get(which);
                        final LinearLayout root = buildColumnEditor(
                                old.name, old.type,
                                old.primaryKey, false, old.notNull, old.unique,
                                old.defaultValue);
                        android.widget.ScrollView scroll = new android.widget.ScrollView(TableActivity.this);
                        scroll.addView(root);
                        new AlertDialog.Builder(TableActivity.this)
                                .setTitle(getString(R.string.modify_column))
                                .setView(scroll)
                                .setPositiveButton(getString(R.string.apply), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        DbHelper.ColumnDef def = readColumnEditor(root);
                                        if (def == null || def.name.isEmpty()) return;
                                        try {
                                            helper.modifyColumn(tableName, old.name, def);
                                            markModified();
                                            columns = helper.getColumns(tableName);
                                            loadData();
                                            Toast.makeText(TableActivity.this, getString(R.string.column_modified), Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                            Toast.makeText(TableActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                })
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                })
                .show();
    }

    private LinearLayout buildColumnEditor(String name, String type,
                                           boolean pk, boolean ai, boolean nn, boolean uq,
                                           String defVal) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 16, 40, 16);

        TextView lblName = new TextView(this);
        lblName.setText(getString(R.string.name_type));
        root.addView(lblName);
        EditText etName = new EditText(this);
        etName.setId(2001);
        etName.setText(name);
        etName.setSingleLine(true);
        root.addView(etName);

        TextView lblType = new TextView(this);
        lblType.setText(getString(R.string.type_label));
        lblType.setPadding(0, 12, 0, 0);
        root.addView(lblType);
        final Spinner spType = new Spinner(this);
        spType.setId(2002);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, DatabaseActivity.COLUMN_TYPES);
        spType.setAdapter(adapter);
        int pos = 0;
        for (int i = 0; i < DatabaseActivity.COLUMN_TYPES.length; i++) {
            if (DatabaseActivity.COLUMN_TYPES[i].equalsIgnoreCase(type)) {
                pos = i;
                break;
            }
        }
        spType.setSelection(pos);
        root.addView(spType);

        LinearLayout checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.HORIZONTAL);
        checks.setPadding(0, 12, 0, 0);
        CheckBox cbPk = new CheckBox(this);
        cbPk.setId(2003);
        cbPk.setText(getString(R.string.primary_key));
        cbPk.setChecked(pk);
        CheckBox cbAi = new CheckBox(this);
        cbAi.setId(2004);
        cbAi.setText(getString(R.string.autoincrement));
        cbAi.setChecked(ai);
        checks.addView(cbPk);
        checks.addView(cbAi);
        root.addView(checks);

        LinearLayout checks2 = new LinearLayout(this);
        checks2.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox cbNn = new CheckBox(this);
        cbNn.setId(2005);
        cbNn.setText(getString(R.string.not_null));
        cbNn.setChecked(nn);
        CheckBox cbUq = new CheckBox(this);
        cbUq.setId(2006);
        cbUq.setText(getString(R.string.unique));
        cbUq.setChecked(uq);
        checks2.addView(cbNn);
        checks2.addView(cbUq);
        root.addView(checks2);

        TextView lblDef = new TextView(this);
        lblDef.setText(getString(R.string.default_optional));
        lblDef.setPadding(0, 12, 0, 0);
        root.addView(lblDef);

        LinearLayout defRow = new LinearLayout(this);
        defRow.setOrientation(LinearLayout.HORIZONTAL);
        defRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final Spinner spDefault = new Spinner(this);
        spDefault.setId(2008);
        String[] presets = DatabaseActivity.defaultPresetsForType(type);
        spDefault.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, presets));
        spDefault.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final EditText etDefault = new EditText(this);
        etDefault.setId(2007);
        etDefault.setHint(getString(R.string.or_type_custom));
        etDefault.setText(defVal != null ? defVal : "");
        etDefault.setSingleLine(true);
        etDefault.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        if (defVal != null && !defVal.isEmpty()) {
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(defVal)) {
                    spDefault.setSelection(i);
                    break;
                }
            }
        }

        spDefault.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                DatabaseActivity.applyDefaultPreset(etDefault, (String) parent.getItemAtPosition(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                String newType = (String) parent.getItemAtPosition(position);
                spDefault.setAdapter(new ArrayAdapter<>(TableActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        DatabaseActivity.defaultPresetsForType(newType)));
                spDefault.setSelection(0);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        defRow.addView(spDefault);
        defRow.addView(etDefault);
        root.addView(defRow);
        return root;
    }

    private DbHelper.ColumnDef readColumnEditor(LinearLayout root) {
        EditText etName = root.findViewById(2001);
        Spinner spType = root.findViewById(2002);
        CheckBox cbPk = root.findViewById(2003);
        CheckBox cbAi = root.findViewById(2004);
        CheckBox cbNn = root.findViewById(2005);
        CheckBox cbUq = root.findViewById(2006);
        EditText etDefault = root.findViewById(2007);
        String cname = etName.getText().toString().trim();
        if (cname.isEmpty()) return null;
        DbHelper.ColumnDef def = new DbHelper.ColumnDef(cname);
        def.type = (String) spType.getSelectedItem();
        def.primaryKey = cbPk.isChecked();
        def.autoIncrement = cbAi.isChecked();
        def.notNull = cbNn.isChecked();
        def.unique = cbUq.isChecked();
        String d = etDefault.getText().toString().trim();
        def.defaultValue = d.isEmpty() ? null : d;
        return def;
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
        }
    }

    class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvPreview;
            Button btnEdit, btnDelete;
            VH(View v) {
                super(v);
                tvPreview = v.findViewById(R.id.tvRowPreview);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, final int pos) {
            Map<String, String> row = rows.get(pos);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Map.Entry<String, String> e : row.entrySet()) {
                if (count > 0) sb.append("  ·  ");
                String disp = e.getValue();
                if (disp != null && disp.startsWith("BLOB:")) {
                    try {
                        int len = Integer.parseInt(disp.split(":", 3)[1]);
                        disp = getString(R.string.blob_preview, len);
                    } catch (Exception ignored) {
                        disp = "BLOB";
                    }
                }
                sb.append(e.getKey()).append("=").append(disp);
                count++;
                if (count >= 5) {
                    sb.append("  …");
                    break;
                }
            }
            h.tvPreview.setText(sb.toString());

            h.btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditDialog(pos, rows.get(pos));
                }
            });

            h.btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(TableActivity.this)
                            .setTitle(getString(R.string.delete_row))
                            .setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    helper.deleteRow(tableName, rowids.get(pos));
                                    markModified();
                                    loadData();
                                }
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                }
            });

            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    final Map<String, String> r = rows.get(pos);
                    String[] items = {getString(R.string.copy_row_csv), getString(R.string.copy_row_text), getString(R.string.duplicate_row), getString(R.string.edit), getString(R.string.delete)};
                    new AlertDialog.Builder(TableActivity.this)
                            .setTitle(getString(R.string.row_actions))
                            .setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == 0) {
                                        StringBuilder csv = new StringBuilder();
                                        boolean first = true;
                                        for (String val : r.values()) {
                                            if (!first) csv.append(",");
                                            first = false;
                                            csv.append(val);
                                        }
                                        copyToClipboard("row", csv.toString());
                                    } else if (which == 1) {
                                        StringBuilder t = new StringBuilder();
                                        for (Map.Entry<String, String> e : r.entrySet()) {
                                            t.append(e.getKey()).append("=").append(e.getValue()).append("\n");
                                        }
                                        copyToClipboard("row", t.toString());
                                    } else if (which == 2) {
                                        // Duplicate: open add dialog with values prefilled
                                        showEditDialog(-1, r);
                                    } else if (which == 3) {
                                        showEditDialog(pos, r);
                                    } else if (which == 4) {
                                        helper.deleteRow(tableName, rowids.get(pos));
                                        markModified();
                                        loadData();
                                    }
                                }
                            })
                            .show();
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static String bytesToHexLimited(byte[] data, int max) {
        if (data == null) return "";
        int n = Math.min(data.length, max);
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X", data[i] & 0xff));
        }
        if (data.length > max) sb.append("…");
        return sb.toString();
    }

    private void maybeShowBlobPreview(final String colName, final String value) {
        if (value == null || !value.startsWith("BLOB:")) return;
        String[] parts = value.split(":", 3);
        int len = 0;
        final String hex = parts.length > 2 ? parts[2] : "";
        try { len = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.blob_preview, len) + "\n\n" + getString(R.string.blob_hex) + ":\n" + hex);
        tv.setTextIsSelectable(true);
        tv.setTextSize(12);
        tv.setPadding(40, 24, 40, 24);
        new AlertDialog.Builder(this)
                .setTitle(R.string.blob_title)
                .setView(tv)
                .setPositiveButton(R.string.copy, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyToClipboard("blob", hex);
                    }
                })
                .setNegativeButton(R.string.ok, null)
                .show();
    }

}
