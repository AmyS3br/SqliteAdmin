package com.amys3labs.sqliteadmin;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        try {
            super.attachBaseContext(LocaleHelper.apply(newBase));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    private static final int REQUEST_OPEN = 1001;
    private static final int REQUEST_CREATE = 1002;
    private static final int REQUEST_SETTINGS = 1003;

    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            prefs = new AppPrefs(this);
            // Apply theme after activity is created (safe path)
            AppCompatDelegate.setDefaultNightMode(AppPrefs.nightModeFor(prefs.getThemeMode()));
        } catch (Throwable t) {
            t.printStackTrace();
            prefs = new AppPrefs(this);
        }
        setContentView(R.layout.activity_main);
        try {
            setupFooter();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQUEST_SETTINGS);
            }
        });

        findViewById(R.id.btnOpen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {
                        "application/x-sqlite3",
                        "application/vnd.sqlite3",
                        "application/octet-stream",
                        "*/*"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_OPEN);
            }
        });

        findViewById(R.id.btnCreate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/x-sqlite3");
                intent.putExtra(Intent.EXTRA_TITLE, "new_database.db");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_CREATE);
            }
        });

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecent();
    }

    private void refreshRecent() {
        LinearLayout container = findViewById(R.id.recentContainer);
        TextView label = findViewById(R.id.tvRecentLabel);
        container.removeAllViews();

        List<AppPrefs.RecentEntry> recent = prefs.getRecent();
        int max = prefs.getRecentCount();
        if (max <= 0 || recent.isEmpty()) {
            label.setVisibility(View.GONE);
            return;
        }
        label.setVisibility(View.VISIBLE);

        int shown = 0;
        for (final AppPrefs.RecentEntry e : recent) {
            if (shown >= max) break;
            if (e.uri == null || e.uri.isEmpty()) continue;

            Button b = new Button(this);
            b.setText(e.name);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        openDatabaseFromUri(Uri.parse(e.uri));
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.cannot_open, ex.getMessage()), Toast.LENGTH_LONG).show();
                    }
                }
            });
            container.addView(b);
            shown++;
        }
        if (shown == 0) label.setVisibility(View.GONE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_EDIT.equals(action)) return;
        final Uri uri = intent.getData();
        if (uri == null) return;

        // Reject obvious non-database files (e.g. .sql)
        String path = uri.toString().toLowerCase();
        String last = uri.getLastPathSegment();
        if (last != null) last = last.toLowerCase();
        if ((last != null && last.endsWith(".sql") && !last.endsWith(".sqlite") && !last.endsWith(".sqlite3"))
                || path.endsWith(".sql")) {
            Toast.makeText(this, R.string.cannot_access_db, Toast.LENGTH_LONG).show();
            return;
        }

        if (SessionState.editorOpen && SessionState.dirty) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsaved_title)
                    .setMessage(R.string.unsaved_open_other)
                    .setPositiveButton(R.string.save_and_open, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (SessionState.dbPath != null && SessionState.originalUri != null) {
                                try {
                                    File local = new File(SessionState.dbPath);
                                    Uri out = Uri.parse(SessionState.originalUri);
                                    MainActivity.writeFileToUri(local, out, getContentResolver());
                                    SessionState.dirty = false;
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                            openDatabaseFromUri(uri);
                        }
                    })
                    .setNegativeButton(R.string.discard_and_open, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SessionState.clear();
                            openDatabaseFromUri(uri);
                        }
                    })
                    .setNeutralButton(R.string.cancel, null)
                    .show();
            return;
        }
        openDatabaseFromUri(uri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS) {
            if (resultCode == RESULT_OK && data != null
                    && data.getBooleanExtra(SettingsActivity.EXTRA_NEED_RECREATE, false)) {
                recreate();
            } else {
                refreshRecent();
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (resultCode != RESULT_CANCELED) {
                Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Uri uri = data.getData();
        final int takeFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored2) {}
        }

        if (requestCode == REQUEST_OPEN) {
            openDatabaseFromUri(uri);
        } else if (requestCode == REQUEST_CREATE) {
            createNewDatabase(uri);
        }
    }

    private void openDatabaseFromUri(Uri uri) {
        try {
            File localFile = copyUriToCache(uri);
            if (localFile == null || !localFile.exists() || localFile.length() == 0) {
                if (localFile != null) {
                    SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(localFile.getAbsolutePath(), null);
                    db.close();
                    writeFileToUri(localFile, uri);
                } else {
                    Toast.makeText(this, R.string.cannot_access_db, Toast.LENGTH_LONG).show();
                    return;
                }
            }

            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    localFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.close();

            rememberRecent(uri, localFile.getName());
            launchDatabaseActivity(localFile, uri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_opening_db, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void createNewDatabase(Uri uri) {
        try {
            String name = "new_" + System.currentTimeMillis() + ".db";
            File localFile = new File(getCacheDir(), name);
            SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(localFile.getAbsolutePath(), null);
            db.close();
            if (!writeFileToUri(localFile, uri)) {
                Toast.makeText(this, R.string.created_local_only, Toast.LENGTH_LONG).show();
            }
            rememberRecent(uri, guessName(uri, localFile.getName()));
            launchDatabaseActivity(localFile, uri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_creating_db, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void rememberRecent(Uri uri, String name) {
        if (uri != null) {
            prefs.addRecent(uri.toString(), name != null ? name : "database");
        }
    }

    private String guessName(Uri uri, String fallback) {
        String last = uri.getLastPathSegment();
        if (last != null) {
            int colon = last.lastIndexOf(':');
            if (colon >= 0) last = last.substring(colon + 1);
            int slash = last.lastIndexOf('/');
            if (slash >= 0) last = last.substring(slash + 1);
            if (!last.isEmpty()) return last;
        }
        return fallback;
    }

    private void launchDatabaseActivity(File localFile, Uri originalUri) {
        Intent i = new Intent(this, DatabaseActivity.class);
        i.putExtra("db_path", localFile.getAbsolutePath());
        i.putExtra("original_uri", originalUri != null ? originalUri.toString() : null);
        startActivity(i);
    }

    private File copyUriToCache(Uri uri) {
        try {
            String name = "db_" + System.currentTimeMillis() + ".db";
            String last = uri.getLastPathSegment();
            if (last != null) {
                int colon = last.lastIndexOf(':');
                if (colon >= 0) last = last.substring(colon + 1);
                int slash = last.lastIndexOf('/');
                if (slash >= 0) last = last.substring(slash + 1);
                if (last.toLowerCase().endsWith(".db")
                        || last.toLowerCase().endsWith(".sqlite")
                        || last.toLowerCase().endsWith(".sqlite3")) {
                    name = last;
                }
            }
            File outFile = new File(getCacheDir(), name);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            in.close();
            return outFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean writeFileToUri(File localFile, Uri uri, android.content.ContentResolver cr) {
        if (localFile == null || !localFile.exists() || uri == null || cr == null) return false;
        try {
            OutputStream out = cr.openOutputStream(uri, "wt");
            if (out == null) out = cr.openOutputStream(uri);
            if (out == null) return false;
            FileInputStream in = new FileInputStream(localFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            in.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean writeFileToUri(File localFile, Uri uri) {
        return writeFileToUri(localFile, uri, getContentResolver());
    }

    private void setupFooter() {
        TextView tv = findViewById(R.id.tvFooter);
        if (tv == null) return;
        String email = getString(R.string.footer_email);
        String grok = getString(R.string.footer_grok);
        String line1 = getString(R.string.footer_line1);
        String line2 = getString(R.string.footer_line2, email);
        String line3 = getString(R.string.footer_line3, grok);
        String full = line1 + "\n" + line2 + "\n" + line3;

        android.text.SpannableString ss = new android.text.SpannableString(full);

        int emailStart = full.indexOf(email);
        if (emailStart >= 0) {
            ss.setSpan(new android.text.style.URLSpan("mailto:" + email),
                    emailStart, emailStart + email.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int grokStart = full.indexOf(grok);
        if (grokStart >= 0) {
            ss.setSpan(new android.text.style.URLSpan("https://x.ai"),
                    grokStart, grokStart + grok.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(ss);
        tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        tv.setLinksClickable(true);
    }

}
