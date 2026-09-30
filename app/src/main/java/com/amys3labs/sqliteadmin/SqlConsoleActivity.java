package com.amys3labs.sqliteadmin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SqlConsoleActivity extends AppCompatActivity {

    private static final int REQ_OPEN_SQL = 3101;

    private String dbPath;
    private DbHelper helper;
    private AppPrefs prefs;
    private EditText etSql;
    private TextView tvResult;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        try {
            super.attachBaseContext(LocaleHelper.apply(newBase));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sql);

        dbPath = getIntent().getStringExtra("db_path");
        helper = new DbHelper(dbPath);
        prefs = new AppPrefs(this);

        etSql = findViewById(R.id.etSql);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setTextIsSelectable(true);
        tvResult.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copyResultToClipboard();
                return true;
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        findViewById(R.id.btnExecute).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                maybeExecute();
            }
        });

        View histBtn = findViewById(R.id.btnHistory);
        if (histBtn != null) {
            histBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQueryHistory();
                }
            });
        }

        View copyBtn = findViewById(R.id.btnCopyResult);
        if (copyBtn != null) {
            copyBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyResultToClipboard();
                }
            });
        }

        View openBtn = findViewById(R.id.btnOpenSql);
        if (openBtn != null) {
            openBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    String[] mimes = {"text/plain", "text/x-sql", "application/sql", "*/*"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
                    startActivityForResult(intent, REQ_OPEN_SQL);
                }
            });
        }

        View explainBtn = findViewById(R.id.btnExplain);
        if (explainBtn != null) {
            explainBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runExplain();
                }
            });
        }

        View favBtn = findViewById(R.id.btnFavorites);
        if (favBtn != null) {
            favBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFavorites();
                }
            });
        }

        setupAutocomplete();
    }

    private void setupAutocomplete() {
        etSql.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                // no popup every keystroke to avoid spam; user long-presses or we offer on demand
            }
        });
        etSql.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showAutocomplete();
                return true;
            }
        });
    }

    private void showAutocomplete() {
        final java.util.List<String> all = helper.getSchemaSuggestions();
        // keywords
        String[] kws = {"SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "DELETE",
                "CREATE", "TABLE", "INDEX", "DROP", "ALTER", "ORDER BY", "GROUP BY", "LIMIT",
                "JOIN", "LEFT JOIN", "INNER JOIN", "ON", "AND", "OR", "NOT", "NULL", "AS"};
        for (String k : kws) {
            if (!all.contains(k)) all.add(0, k);
        }
        final String[] items = all.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.autocomplete_hint)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        insertAtCursor(items[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void insertAtCursor(String text) {
        int start = Math.max(0, etSql.getSelectionStart());
        int end = Math.max(0, etSql.getSelectionEnd());
        etSql.getText().replace(Math.min(start, end), Math.max(start, end), text);
        etSql.setSelection(Math.min(start, end) + text.length());
    }

    private void runExplain() {
        String sql = etSql.getText().toString().trim();
        if (sql.isEmpty()) return;
        // strip trailing semicolon
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        try {
            Cursor c = helper.rawQuery("EXPLAIN QUERY PLAN " + sql);
            StringBuilder sb = new StringBuilder();
            String[] names = c.getColumnNames();
            for (int i = 0; i < names.length; i++) {
                if (i > 0) sb.append(" | ");
                sb.append(names[i]);
            }
            sb.append("\n");
            while (c.moveToNext()) {
                for (int i = 0; i < names.length; i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(c.isNull(i) ? "NULL" : c.getString(i));
                }
                sb.append("\n");
            }
            c.close();
            tvResult.setText(sb.toString());
        } catch (Exception e) {
            tvResult.setText(getString(R.string.sql_error, e.getMessage()));
        }
    }

    private void showFavorites() {
        final java.util.List<String[]> favs = prefs.getFavorites();
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add("+ " + getString(R.string.save_favorite));
        for (String[] f : favs) {
            String preview = f[1].replace("\n", " ");
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            labels.add(f[0] + " — " + preview);
        }
        if (favs.isEmpty()) {
            // still allow save
        }
        final String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.favorites)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            promptSaveFavorite();
                        } else {
                            etSql.setText(favs.get(which - 1)[1]);
                            etSql.setSelection(etSql.getText().length());
                        }
                    }
                })
                .setNeutralButton(R.string.delete_favorite, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        promptDeleteFavorite(favs);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptSaveFavorite() {
        final String sql = etSql.getText().toString().trim();
        if (sql.isEmpty()) return;
        final EditText et = new EditText(this);
        et.setHint(R.string.favorite_name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_favorite)
                .setView(et)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) name = "Query";
                        prefs.addFavorite(name, sql);
                        Toast.makeText(SqlConsoleActivity.this, R.string.favorite_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptDeleteFavorite(final java.util.List<String[]> favs) {
        if (favs.isEmpty()) {
            Toast.makeText(this, R.string.no_favorites, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[favs.size()];
        for (int i = 0; i < favs.size(); i++) names[i] = favs.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_favorite)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.removeFavorite(favs.get(which)[0]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_SQL || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("null stream");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            in.close();
            etSql.setText(sb.toString());
            Toast.makeText(this, R.string.sql_file_loaded, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.sql_file_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (helper != null) helper.close();
    }

    private void maybeExecute() {
        final String sql = etSql.getText().toString().trim();
        if (sql.isEmpty()) return;
        prefs.addQueryHistory(sql);

        if (prefs.isConfirmDestructiveSql() && looksDestructive(sql)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.destructive_sql_title)
                    .setMessage(getString(R.string.destructive_sql_msg, sql.length() > 500 ? sql.substring(0, 500) + "…" : sql))
                    .setPositiveButton(R.string.execute_btn, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            executeSql(sql);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            executeSql(sql);
        }
    }

    private boolean looksDestructive(String sql) {
        String lower = sql.toLowerCase();
        return lower.contains("drop ")
                || lower.contains("delete ")
                || lower.contains("truncate ")
                || lower.contains("alter ")
                || lower.contains("update ")
                || lower.contains("insert ")
                || lower.contains("replace ")
                || lower.contains("create ")
                || lower.contains("attach ")
                || lower.contains("detach ");
    }

    private void executeSql(String sql) {
        try {
            String lower = sql.toLowerCase().trim();
            if (lower.startsWith("select") || lower.startsWith("pragma")
                    || lower.startsWith("explain") || lower.startsWith("with")) {
                Cursor c = helper.rawQuery(sql);
                StringBuilder sb = new StringBuilder();
                String[] names = c.getColumnNames();
                for (int i = 0; i < names.length; i++) {
                    if (i > 0) sb.append(" | ");
                    sb.append(names[i]);
                }
                sb.append("\n");
                for (int i = 0; i < names.length; i++) {
                    if (i > 0) sb.append("-+-");
                    sb.append("----");
                }
                sb.append("\n");

                int rows = 0;
                while (c.moveToNext() && rows < 500) {
                    for (int i = 0; i < names.length; i++) {
                        if (i > 0) sb.append(" | ");
                        sb.append(c.isNull(i) ? "NULL" : c.getString(i));
                    }
                    sb.append("\n");
                    rows++;
                }
                c.close();
                sb.append("\n(").append(rows).append(rows >= 500 ? "+ rows, truncated)" : " rows)");
                tvResult.setText(sb.toString());
            } else {
                String[] parts = sql.split(";");
                int executed = 0;
                StringBuilder errors = new StringBuilder();
                java.util.ArrayList<String> batch = new java.util.ArrayList<>();
                for (String part : parts) {
                    String s = stripSqlComments(part);
                    if (s.isEmpty()) continue;
                    String su = s.toUpperCase().replaceAll("\\s+", " ").trim();
                    // Dump files may include BEGIN/COMMIT — strip (we auto-commit per statement)
                    if (su.equals("BEGIN") || su.equals("BEGIN TRANSACTION")
                            || su.equals("BEGIN IMMEDIATE") || su.equals("BEGIN EXCLUSIVE")
                            || su.equals("COMMIT") || su.equals("END") || su.equals("END TRANSACTION")
                            || su.equals("ROLLBACK") || su.equals("ROLLBACK TRANSACTION")) {
                        continue;
                    }
                    if (su.contains("ANDROID_METADATA")) {
                        continue;
                    }
                    batch.add(s);
                }
                // Auto-commit each statement. A single beginTransaction()/endTransaction()
                // would ROLL BACK earlier successes (e.g. CREATE TABLE) if any later
                // INSERT failed — leaving "no such table" for everything.
                android.database.sqlite.SQLiteDatabase db = helper.getDb();
                for (String s : batch) {
                    if (s == null || s.trim().isEmpty()) continue;
                    try {
                        db.execSQL(s);
                        executed++;
                    } catch (Exception ex) {
                        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                        String low = msg.toLowerCase();
                        if (low.contains("already exists")
                                || low.contains("not an error")
                                || low.contains("sqlite_ok")
                                || low.contains("code 0")) {
                            executed++;
                            continue;
                        }
                        if (errors.length() > 0) errors.append("\n");
                        errors.append(msg);
                    }
                }
                if (errors.length() > 0) {
                    tvResult.setText(getString(R.string.sql_error, errors.toString())
                            + "\n\n(" + executed + " ok)");
                } else {
                    tvResult.setText(getString(R.string.sql_ok, executed));
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.sql_success_toast, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            tvResult.setText(getString(R.string.sql_error, e.getMessage()));
        }
    }


    /** Remove full-line -- comments; keep real SQL (even if comments precede it in the same ; chunk). */
    private static String stripSqlComments(String sql) {
        if (sql == null) return "";
        StringBuilder out = new StringBuilder();
        String[] lines = sql.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("--")) continue;
            out.append(line).append("\n");
        }
        return out.toString().trim();
    }


    private void showQueryHistory() {
        final java.util.List<String> hist = prefs.getQueryHistory();
        if (hist.isEmpty()) {
            Toast.makeText(this, R.string.query_history_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = new String[hist.size()];
        for (int i = 0; i < hist.size(); i++) {
            String s = hist.get(i).replace("\n", " ").replace("\r", " ");
            if (s.length() > 200) s = s.substring(0, 200) + "…";
            items[i] = s;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v.findViewById(android.R.id.text1);
                if (tv != null) {
                    tv.setTextSize(12);
                    tv.setPadding(24, 20, 24, 20);
                }
                return v;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.query_history_title)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        etSql.setText(hist.get(which));
                        etSql.setSelection(etSql.getText().length());
                    }
                })
                .setNeutralButton(R.string.clear_history, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.clearQueryHistory();
                        Toast.makeText(SqlConsoleActivity.this, R.string.recent_cleared, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void copyResultToClipboard() {
        CharSequence text = tvResult.getText();
        if (text == null || text.length() == 0) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("sql_result", text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

}
