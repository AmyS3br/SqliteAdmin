package com.amys3labs.sqliteadmin;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DatabaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        try {
            super.attachBaseContext(LocaleHelper.apply(newBase));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    public static final String[] COLUMN_TYPES = {
            "INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC",
            "BOOLEAN", "DATE", "DATETIME", "TIMESTAMP"
    };


    /** Preset DEFAULT values shown in the dropdown, depending on column type. */
    public static String[] defaultPresetsForType(String type) {
        if (type == null) type = "TEXT";
        String t = type.toUpperCase();
        if (t.contains("TIMESTAMP") || t.equals("DATETIME")) {
            return new String[]{
                    "(none)",
                    "CURRENT_TIMESTAMP",
                    "datetime('now')",
                    "datetime('now', 'localtime')",
                    "CURRENT_DATE",
                    "CURRENT_TIME",
                    "NULL"
            };
        }
        if (t.equals("DATE")) {
            return new String[]{
                    "(none)",
                    "CURRENT_DATE",
                    "date('now')",
                    "date('now', 'localtime')",
                    "CURRENT_TIMESTAMP",
                    "NULL"
            };
        }
        if (t.equals("TIME")) {
            return new String[]{
                    "(none)",
                    "CURRENT_TIME",
                    "time('now')",
                    "time('now', 'localtime')",
                    "NULL"
            };
        }
        if (t.contains("INT") || t.equals("BOOLEAN")) {
            return new String[]{
                    "(none)",
                    "0",
                    "1",
                    "-1",
                    "NULL"
            };
        }
        if (t.equals("REAL") || t.equals("FLOAT") || t.equals("DOUBLE") || t.equals("NUMERIC")) {
            return new String[]{
                    "(none)",
                    "0",
                    "0.0",
                    "1.0",
                    "NULL"
            };
        }
        if (t.equals("BLOB")) {
            return new String[]{
                    "(none)",
                    "NULL"
            };
        }
        // TEXT and others
        return new String[]{
                "(none)",
                "''",
                "'N/A'",
                "CURRENT_TIMESTAMP",
                "CURRENT_DATE",
                "NULL"
        };
    }

    /** Apply a preset label into the DEFAULT edit field. */
    public static void applyDefaultPreset(EditText etDefault, String preset) {
        if (etDefault == null || preset == null) return;
        if ("(none)".equals(preset)) {
            etDefault.setText("");
        } else if ("''".equals(preset)) {
            etDefault.setText("''");
        } else {
            etDefault.setText(preset);
        }
        etDefault.setSelection(etDefault.getText().length());
    }


    private static final int REQ_TABLE = 2001;
    private static final int REQ_SQL = 2002;
    private static final int REQ_EXPORT_CSV = 2003;
    private static final int REQ_EXPORT_SQL = 2004;
    private static final int REQ_IMPORT_CSV = 2005;
    private static final int REQ_EXPORT_JSON = 2006;
    private static final int REQ_SETTINGS = 2007;

    private String dbPath;
    private String originalUriString;
    private DbHelper helper;
    private RecyclerView recycler;
    private TableAdapter adapter;
    private final List<String> tables = new ArrayList<>();
    private TextView tvSaveStatus;
    private boolean dirty = false;
    private AppPrefs prefs;

    // pending export content waiting for SAF create result
    private String pendingExportContent;
    private String pendingExportMime;
    private String pendingImportTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);

        dbPath = getIntent().getStringExtra("db_path");
        originalUriString = getIntent().getStringExtra("original_uri");
        if (dbPath == null) {
            finish();
            return;
        }

        prefs = new AppPrefs(this);
        helper = new DbHelper(dbPath);
        SessionState.editorOpen = true;
        SessionState.dbPath = dbPath;
        SessionState.originalUri = originalUriString;
        SessionState.dirty = dirty;

        TextView tvDbName = findViewById(R.id.tvDbName);
        tvDbName.setText(new File(dbPath).getName());

        tvSaveStatus = findViewById(R.id.tvSaveStatus);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                maybeConfirmExit();
            }
        });

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToOriginal();
            }
        });

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivityForResult(new Intent(DatabaseActivity.this, SettingsActivity.class), REQ_SETTINGS);
                }
            });
        }

        findViewById(R.id.btnNewTable).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateTableDialog();
            }
        });

        findViewById(R.id.btnSql).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(DatabaseActivity.this, SqlConsoleActivity.class);
                i.putExtra("db_path", dbPath);
                startActivityForResult(i, REQ_SQL);
            }
        });

        findViewById(R.id.btnExport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportImportMenu();
            }
        });

        findViewById(R.id.btnInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = helper.getDatabaseInfo(DatabaseActivity.this)
                        + "\n"
                        + getString(R.string.info_fk_pragma,
                                helper.foreignKeysEnabled()
                                        ? getString(R.string.fk_on)
                                        : getString(R.string.fk_off));
                new AlertDialog.Builder(DatabaseActivity.this)
                        .setTitle(getString(R.string.database_info))
                        .setMessage(msg)
                        .setPositiveButton(getString(R.string.ok), null)
                        .setNeutralButton(getString(R.string.toggle_fks), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean on = !helper.foreignKeysEnabled();
                                helper.setForeignKeys(on);
                                Toast.makeText(DatabaseActivity.this,
                                        on ? getString(R.string.foreign_keys_on) : getString(R.string.foreign_keys_off),
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });

        recycler = findViewById(R.id.recyclerTables);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TableAdapter();
        recycler.setAdapter(adapter);

        refreshTables();
        updateSaveStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTables();
        // Do NOT mark dirty here – only mark when we know something changed
        updateSaveStatus();
    }

    @Override
    public void onBackPressed() {
        maybeConfirmExit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (helper != null) helper.close();
        if (isFinishing()) {
            SessionState.clear();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_TABLE || requestCode == REQ_SQL) {
            if (resultCode == RESULT_OK) {
                markDirty();
            }
            refreshTables();
            return;
        }

        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_EXPORT_CSV || requestCode == REQ_EXPORT_SQL || requestCode == REQ_EXPORT_JSON) {
            writePendingExport(uri);
        } else if (requestCode == REQ_IMPORT_CSV) {
            importCsvFromUri(uri);
        }
    }

    private void maybeConfirmExit() {
        if (dirty && originalUriString != null) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.unsaved_title))
                    .setMessage(getString(R.string.unsaved_message))
                    .setPositiveButton(getString(R.string.save_and_exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (saveToOriginal()) finish();
                        }
                    })
                    .setNegativeButton(getString(R.string.discard), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNeutralButton(getString(R.string.cancel), null)
                    .show();
        } else {
            finish();
        }
    }

    private boolean saveToOriginal() {
        if (originalUriString == null || originalUriString.isEmpty()) {
            Toast.makeText(this, R.string.no_original, Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            helper.checkpoint();
            helper.close();

            File local = new File(dbPath);
            Uri uri = Uri.parse(originalUriString);
            boolean ok = MainActivity.writeFileToUri(local, uri, getContentResolver());

            helper = new DbHelper(dbPath);

            if (ok) {
                dirty = false;
                SessionState.dirty = false;
                updateSaveStatus();
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
                return true;
            } else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.save_error, e.getMessage()), Toast.LENGTH_LONG).show();
            try {
                helper = new DbHelper(dbPath);
            } catch (Exception ignored) {}
            return false;
        }
    }

    private void updateSaveStatus() {
        if (tvSaveStatus == null) return;
        if (originalUriString == null) {
            tvSaveStatus.setVisibility(View.VISIBLE);
            tvSaveStatus.setText(R.string.working_local_only);
        } else if (dirty) {
            tvSaveStatus.setVisibility(View.VISIBLE);
            tvSaveStatus.setText(getString(R.string.unsaved_status_alt));
        } else {
            tvSaveStatus.setVisibility(View.GONE);
        }
    }

    private void markDirty() {
        dirty = true;
        SessionState.dirty = true;
        updateSaveStatus();
        if (prefs != null && prefs.isAutoSave() && originalUriString != null) {
            saveToOriginal();
        }
    }

    private void refreshTables() {
        tables.clear();
        tables.addAll(helper.getTables());
        adapter.notifyDataSetChanged();
    }

    // ── Export / Import menu ───────────────────────────────────────────────

    private void showExportImportMenu() {
        String[] items = {
                getString(R.string.export_table_csv),
                getString(R.string.export_table_json),
                getString(R.string.export_db_sql),
                getString(R.string.import_csv),
                getString(R.string.vacuum_db),
                getString(R.string.analyze_db),
                getString(R.string.integrity_check),
                getString(R.string.attach_db),
                getString(R.string.pragmas)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.export_import_maintain))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) pickTableThenExportCsv();
                        else if (which == 1) pickTableThenExportJson();
                        else if (which == 2) exportSqlDump();
                        else if (which == 3) pickTableThenImportCsv();
                        else if (which == 4) runVacuum();
                        else if (which == 5) runAnalyze();
                        else if (which == 6) runIntegrityCheck();
                        else if (which == 7) showAttachDialog();
                        else if (which == 8) showPragmaEditor();
                    }
                })
                .show();
    }

    private void pickTableThenExportCsv() {
        if (tables.isEmpty()) {
            Toast.makeText(this, R.string.no_tables_export, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = tables.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.export_which_table))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String table = names[which];
                        try {
                            pendingExportContent = helper.exportTableToCsv(table);
                            pendingExportMime = "text/csv";
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("text/csv");
                            intent.putExtra(Intent.EXTRA_TITLE, table + ".csv");
                            startActivityForResult(intent, REQ_EXPORT_CSV);
                        } catch (Exception e) {
                            Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private void exportSqlDump() {
        try {
            pendingExportContent = helper.exportSqlDump();
            pendingExportMime = "application/sql";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/*");
            String base = new File(dbPath).getName();
            if (base.contains(".")) base = base.substring(0, base.lastIndexOf('.'));
            intent.putExtra(Intent.EXTRA_TITLE, base + "_dump.sql");
            startActivityForResult(intent, REQ_EXPORT_SQL);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pickTableThenImportCsv() {
        // Offer existing tables + "New table…"
        List<String> options = new ArrayList<>(tables);
        options.add(0, getString(R.string.new_table_from_csv));
        final String[] names = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_csv_into))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            final EditText et = new EditText(DatabaseActivity.this);
                            et.setHint(getString(R.string.new_table_name));
                            et.setSingleLine(true);
                            new AlertDialog.Builder(DatabaseActivity.this)
                                    .setTitle(getString(R.string.new_table_name))
                                    .setView(et)
                                    .setPositiveButton(getString(R.string.continue_btn), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            String name = et.getText().toString().trim();
                                            if (name.isEmpty()) return;
                                            pendingImportTable = name;
                                            launchImportCsvPicker();
                                        }
                                    })
                                    .setNegativeButton(getString(R.string.cancel), null)
                                    .show();
                        } else {
                            pendingImportTable = names[which];
                            launchImportCsvPicker();
                        }
                    }
                })
                .show();
    }

    private void launchImportCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimes = {"text/csv", "text/comma-separated-values", "text/plain", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        startActivityForResult(intent, REQ_IMPORT_CSV);
    }

    private void writePendingExport(Uri uri) {
        if (pendingExportContent == null) return;
        try {
            OutputStream out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new Exception("Cannot open output stream");
            out.write(pendingExportContent.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            pendingExportContent = null;
        }
    }

    private void importCsvFromUri(Uri uri) {
        if (pendingImportTable == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("Cannot read file");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            in.close();
            String csv = new String(bos.toByteArray(), StandardCharsets.UTF_8);

            boolean create = !helper.getTables().contains(pendingImportTable);
            int rows = helper.importCsv(pendingImportTable, csv, create);
            refreshTables();
            markDirty();
            Toast.makeText(this, getString(R.string.imported_rows_into, rows, pendingImportTable),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        } finally {
            pendingImportTable = null;
        }
    }


    private void pickTableThenExportJson() {
        if (tables.isEmpty()) {
            Toast.makeText(this, R.string.no_tables_export, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] names = tables.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.export_which_table_json))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String table = names[which];
                        try {
                            pendingExportContent = helper.exportTableToJson(table);
                            pendingExportMime = "application/json";
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/json");
                            intent.putExtra(Intent.EXTRA_TITLE, table + ".json");
                            startActivityForResult(intent, REQ_EXPORT_JSON);
                        } catch (Exception e) {
                            Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
    }

    private void runVacuum() {
        try {
            helper.vacuum();
            markDirty();
            Toast.makeText(this, R.string.vacuum_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runAnalyze() {
        try {
            helper.analyze();
            Toast.makeText(this, R.string.analyze_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void runIntegrityCheck() {
        try {
            String result = helper.integrityCheck();
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.integrity_check))
                    .setMessage(result)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Create Table dialog ────────────────────────────────────────────────

    private void showCreateTableDialog() {
        final EditText etName = new EditText(this);
        etName.setHint(getString(R.string.table_name));
        etName.setSingleLine(true);

        final LinearLayout colsLayout = new LinearLayout(this);
        colsLayout.setOrientation(LinearLayout.VERTICAL);

        addColumnRow(colsLayout, "id", "INTEGER", true, true, false, false, null);

        Button btnAddCol = new Button(this);
        btnAddCol.setText(getString(R.string.add_column_btn));
        btnAddCol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addColumnRow(colsLayout, "", "TEXT", false, false, false, false, null);
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 24, 40, 16);
        TextView label = new TextView(this);
        label.setText(getString(R.string.table_name));
        label.setTextSize(13);
        root.addView(label);
        root.addView(etName);
        TextView colsLabel = new TextView(this);
        colsLabel.setText(getString(R.string.columns));
        colsLabel.setTextSize(13);
        colsLabel.setPadding(0, 16, 0, 4);
        root.addView(colsLabel);
        root.addView(colsLayout);
        root.addView(btnAddCol);

        final android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.create_table))
                .setView(scroll)
                .setPositiveButton(getString(R.string.create), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(DatabaseActivity.this, R.string.table_name_required, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<DbHelper.ColumnDef> cols = collectColumnDefs(colsLayout);
                        if (cols.isEmpty()) {
                            Toast.makeText(DatabaseActivity.this, R.string.at_least_one_column, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            helper.createTable(name, cols);
                            refreshTables();
                            markDirty();
                            Toast.makeText(DatabaseActivity.this, getString(R.string.table_created_named, name), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void addColumnRow(LinearLayout parent,
                              String name, String type,
                              boolean pk, boolean ai, boolean nn, boolean uq,
                              String defVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 8);
        row.setBackgroundColor(0x08000000);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);

        EditText etName = new EditText(this);
        etName.setId(1001);
        etName.setHint(getString(R.string.column_name_hint));
        etName.setText(name);
        etName.setSingleLine(true);
        etName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        Spinner spType = new Spinner(this);
        spType.setId(1002);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, COLUMN_TYPES);
        spType.setAdapter(typeAdapter);
        int typePos = 0;
        for (int i = 0; i < COLUMN_TYPES.length; i++) {
            if (COLUMN_TYPES[i].equalsIgnoreCase(type)) {
                typePos = i;
                break;
            }
        }
        spType.setSelection(typePos);
        spType.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(etName);
        top.addView(spType);
        row.addView(top);

        LinearLayout checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.HORIZONTAL);
        checks.setPadding(0, 4, 0, 0);

        CheckBox cbPk = new CheckBox(this);
        cbPk.setId(1003);
        cbPk.setText(getString(R.string.pk_short));
        cbPk.setChecked(pk);
        cbPk.setTextSize(12);

        CheckBox cbAi = new CheckBox(this);
        cbAi.setId(1004);
        cbAi.setText(getString(R.string.ai_short));
        cbAi.setChecked(ai);
        cbAi.setTextSize(12);

        CheckBox cbNn = new CheckBox(this);
        cbNn.setId(1005);
        cbNn.setText(getString(R.string.nn_short));
        cbNn.setChecked(nn);
        cbNn.setTextSize(12);

        CheckBox cbUq = new CheckBox(this);
        cbUq.setId(1006);
        cbUq.setText(getString(R.string.uq_short));
        cbUq.setChecked(uq);
        cbUq.setTextSize(12);

        checks.addView(cbPk);
        checks.addView(cbAi);
        checks.addView(cbNn);
        checks.addView(cbUq);
        row.addView(checks);

        TextView lblDef = new TextView(this);
        lblDef.setText(getString(R.string.default_label));
        lblDef.setTextSize(12);
        lblDef.setPadding(0, 6, 0, 0);
        row.addView(lblDef);

        LinearLayout defRow = new LinearLayout(this);
        defRow.setOrientation(LinearLayout.HORIZONTAL);
        defRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final Spinner spDefault = new Spinner(this);
        spDefault.setId(1008);
        String[] presets = defaultPresetsForType(type);
        ArrayAdapter<String> defAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, presets);
        spDefault.setAdapter(defAdapter);
        spDefault.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        EditText etDefault = new EditText(this);
        etDefault.setId(1007);
        etDefault.setHint(getString(R.string.or_type_custom));
        etDefault.setText(defVal != null ? defVal : "");
        etDefault.setSingleLine(true);
        etDefault.setTextSize(13);
        etDefault.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));

        // Pre-select matching preset if defVal matches
        if (defVal != null && !defVal.isEmpty()) {
            boolean matched = false;
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(defVal) || ("''".equals(presets[i]) && "''".equals(defVal))) {
                    spDefault.setSelection(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                // leave (none), keep custom text
                spDefault.setSelection(0);
            }
        }

        spDefault.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; } // don't overwrite initial defVal
                String preset = (String) parent.getItemAtPosition(position);
                applyDefaultPreset(etDefault, preset);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // When type changes, refresh default presets
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private boolean first = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (first) { first = false; return; }
                String newType = (String) parent.getItemAtPosition(position);
                String[] newPresets = defaultPresetsForType(newType);
                ArrayAdapter<String> a = new ArrayAdapter<>(DatabaseActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, newPresets);
                spDefault.setAdapter(a);
                spDefault.setSelection(0);
                // clear custom only if it was a previous preset? keep custom text
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        defRow.addView(spDefault);
        defRow.addView(etDefault);
        row.addView(defRow);

        parent.addView(row);
    }

    private List<DbHelper.ColumnDef> collectColumnDefs(LinearLayout colsLayout) {
        List<DbHelper.ColumnDef> cols = new ArrayList<>();
        for (int i = 0; i < colsLayout.getChildCount(); i++) {
            View row = colsLayout.getChildAt(i);
            EditText etColName = row.findViewById(1001);
            Spinner spType = row.findViewById(1002);
            CheckBox cbPk = row.findViewById(1003);
            CheckBox cbAi = row.findViewById(1004);
            CheckBox cbNn = row.findViewById(1005);
            CheckBox cbUq = row.findViewById(1006);
            EditText etDefault = row.findViewById(1007);

            if (etColName == null) continue;
            String cname = etColName.getText().toString().trim();
            if (cname.isEmpty()) continue;

            DbHelper.ColumnDef def = new DbHelper.ColumnDef(cname);
            def.type = spType != null ? (String) spType.getSelectedItem() : "TEXT";
            def.primaryKey = cbPk != null && cbPk.isChecked();
            def.autoIncrement = cbAi != null && cbAi.isChecked();
            def.notNull = cbNn != null && cbNn.isChecked();
            def.unique = cbUq != null && cbUq.isChecked();
            if (etDefault != null) {
                String d = etDefault.getText().toString().trim();
                def.defaultValue = d.isEmpty() ? null : d;
            }
            cols.add(def);
        }
        return cols;
    }

    // ── Table list adapter ─────────────────────────────────────────────────

    class TableAdapter extends RecyclerView.Adapter<TableAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvName;
            Button btnDrop;
            VH(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvTableName);
                btnDrop = v.findViewById(R.id.btnDrop);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_table, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            final String table = tables.get(pos);
            h.tvName.setText(table);

            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(DatabaseActivity.this, TableActivity.class);
                    i.putExtra("db_path", dbPath);
                    i.putExtra("table_name", table);
                    startActivityForResult(i, REQ_TABLE);
                }
            });

            h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showTableContextMenu(table);
                    return true;
                }
            });

            h.btnDrop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(DatabaseActivity.this)
                            .setTitle(getString(R.string.drop_table_title))
                            .setMessage("Delete table \"" + table + "\" permanently? This cannot be undone.")
                            .setPositiveButton(getString(R.string.drop), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    helper.dropTable(table);
                                    refreshTables();
                                    markDirty();
                                }
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return tables.size();
        }
    }

    private void showTableContextMenu(final String table) {
        String[] items = {"Open", "Rename", "Export CSV", "Drop"};
        new AlertDialog.Builder(this)
                .setTitle(table)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent i = new Intent(DatabaseActivity.this, TableActivity.class);
                            i.putExtra("db_path", dbPath);
                            i.putExtra("table_name", table);
                            startActivityForResult(i, REQ_TABLE);
                        } else if (which == 1) {
                            final EditText et = new EditText(DatabaseActivity.this);
                            et.setText(table);
                            et.setSingleLine(true);
                            new AlertDialog.Builder(DatabaseActivity.this)
                                    .setTitle(getString(R.string.rename_table))
                                    .setView(et)
                                    .setPositiveButton(getString(R.string.rename), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            String newName = et.getText().toString().trim();
                                            if (newName.isEmpty() || newName.equals(table)) return;
                                            try {
                                                helper.renameTable(table, newName);
                                                refreshTables();
                                                markDirty();
                                                Toast.makeText(DatabaseActivity.this, R.string.renamed_toast, Toast.LENGTH_SHORT).show();
                                            } catch (Exception e) {
                                                Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    })
                                    .setNegativeButton(getString(R.string.cancel), null)
                                    .show();
                        } else if (which == 2) {
                            try {
                                pendingExportContent = helper.exportTableToCsv(table);
                                pendingExportMime = "text/csv";
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("text/csv");
                                intent.putExtra(Intent.EXTRA_TITLE, table + ".csv");
                                startActivityForResult(intent, REQ_EXPORT_CSV);
                            } catch (Exception e) {
                                Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else if (which == 3) {
                            new AlertDialog.Builder(DatabaseActivity.this)
                                    .setTitle(getString(R.string.drop_table_title))
                                    .setMessage("Delete \"" + table + "\" permanently?")
                                    .setPositiveButton(getString(R.string.drop), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            helper.dropTable(table);
                                            refreshTables();
                                            markDirty();
                                        }
                                    })
                                    .setNegativeButton(getString(R.string.cancel), null)
                                    .show();
                        }
                    }
                })
                .show();
    }

    private static final int REQ_ATTACH = 2010;
    private String pendingAttachAlias;

    private void showAttachDialog() {
        final EditText etAlias = new EditText(this);
        etAlias.setHint(R.string.attach_alias);
        etAlias.setText("other");
        new AlertDialog.Builder(this)
                .setTitle(R.string.attach_db)
                .setView(etAlias)
                .setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pendingAttachAlias = etAlias.getText().toString().trim();
                        if (pendingAttachAlias.isEmpty()) pendingAttachAlias = "other";
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        startActivityForResult(intent, REQ_ATTACH);
                    }
                })
                .setNeutralButton(R.string.detach_db, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        promptDetach();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptDetach() {
        final EditText et = new EditText(this);
        et.setHint(R.string.attach_alias);
        new AlertDialog.Builder(this)
                .setTitle(R.string.detach_db)
                .setView(et)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String alias = et.getText().toString().trim();
                        if (alias.isEmpty()) return;
                        try {
                            helper.detachDatabase(alias);
                            Toast.makeText(DatabaseActivity.this, getString(R.string.detach_ok, alias), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPragmaEditor() {
        final String[] names = {
                "foreign_keys", "journal_mode", "synchronous", "cache_size",
                "page_size", "user_version", "auto_vacuum", "encoding", "busy_timeout"
        };
        StringBuilder info = new StringBuilder();
        for (String n : names) {
            try {
                info.append(n).append(" = ").append(helper.getPragma(n)).append("\n");
            } catch (Exception e) {
                info.append(n).append(" = ?\n");
            }
        }
        final EditText etName = new EditText(this);
        etName.setHint("PRAGMA name");
        final EditText etVal = new EditText(this);
        etVal.setHint("value");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 20, 40, 10);
        TextView tv = new TextView(this);
        tv.setText(info.toString());
        tv.setTextSize(12);
        tv.setPadding(0, 0, 0, 16);
        box.addView(tv);
        box.addView(etName);
        box.addView(etVal);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pragma_editor)
                .setView(box)
                .setPositiveButton(R.string.pragma_set, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String n = etName.getText().toString().trim();
                        String v = etVal.getText().toString().trim();
                        if (n.isEmpty()) return;
                        try {
                            helper.setPragma(n, v);
                            markDirty();
                            Toast.makeText(DatabaseActivity.this, n + " = " + helper.getPragma(n), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(DatabaseActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

}
