package com.amys3labs.sqliteadmin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_NEED_RECREATE = "need_recreate";

    private AppPrefs prefs;
    private EditText etPageSize;
    private EditText etRecentCount;
    private CheckBox cbAutoSave;
    private CheckBox cbConfirmSql;
    private Spinner spTheme;
    private CheckBox cbConfirmDrop;
    private EditText etMaxHistory;
    private Spinner spLanguage;

    private static final String[] LANG_CODES = {"", "en", "pt", "es", "de", "fr", "ru", "zh", "ja", "hi"};

    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            super.attachBaseContext(LocaleHelper.apply(newBase));
        } catch (Throwable t) {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        setContentView(R.layout.activity_settings);

        etPageSize = findViewById(R.id.etPageSize);
        etRecentCount = findViewById(R.id.etRecentCount);
        cbAutoSave = findViewById(R.id.cbAutoSave);
        cbConfirmSql = findViewById(R.id.cbConfirmSql);
        spTheme = findViewById(R.id.spTheme);
        if (spTheme != null) {
            String[] themeLabels = new String[]{
                    getString(R.string.theme_system),
                    getString(R.string.theme_light),
                    getString(R.string.theme_dark)
            };
            spTheme.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, themeLabels));
            String tm = prefs.getThemeMode();
            int ti = "light".equals(tm) ? 1 : ("dark".equals(tm) ? 2 : 0);
            spTheme.setSelection(ti);
        }
        cbConfirmDrop = findViewById(R.id.cbConfirmDrop);
        etMaxHistory = findViewById(R.id.etMaxHistory);
        spLanguage = findViewById(R.id.spLanguage);

        String[] labels = new String[]{
                getString(R.string.language_system),
                getString(R.string.language_en),
                getString(R.string.language_pt),
                getString(R.string.language_es),
                getString(R.string.language_de),
                getString(R.string.language_fr),
                getString(R.string.language_ru),
                getString(R.string.language_zh),
                getString(R.string.language_ja),
                getString(R.string.language_hi)
        };
        spLanguage.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));

        etPageSize.setText(String.valueOf(prefs.getPageSize()));
        etRecentCount.setText(String.valueOf(prefs.getRecentCount()));
        cbAutoSave.setChecked(prefs.isAutoSave());
        cbConfirmSql.setChecked(prefs.isConfirmDestructiveSql());
        if (cbConfirmDrop != null) cbConfirmDrop.setChecked(prefs.isConfirmDrop());
        if (etMaxHistory != null) etMaxHistory.setText(String.valueOf(prefs.getMaxQueryHistory()));

        String lang = prefs.getLanguage();
        int langPos = 0;
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(lang)) {
                langPos = i;
                break;
            }
        }
        spLanguage.setSelection(langPos);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btnSaveSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        findViewById(R.id.btnClearRecent).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.clearRecent();
                Toast.makeText(SettingsActivity.this, R.string.recent_cleared, Toast.LENGTH_SHORT).show();
            }
        });

        View about = findViewById(R.id.btnAbout);
        if (about != null) {
            about.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
                }
            });
        }
    }

    private void save() {
        try {
            prefs.setPageSize(Integer.parseInt(etPageSize.getText().toString().trim()));
        } catch (Exception e) {
            Toast.makeText(this, R.string.invalid_page_size, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            prefs.setRecentCount(Integer.parseInt(etRecentCount.getText().toString().trim()));
        } catch (Exception e) {
            Toast.makeText(this, R.string.invalid_recent_count, Toast.LENGTH_SHORT).show();
            return;
        }

        String oldLang = prefs.getLanguage();
        String oldTheme = prefs.getThemeMode();

        prefs.setAutoSave(cbAutoSave.isChecked());
        prefs.setConfirmDestructiveSql(cbConfirmSql.isChecked());
        if (spTheme != null) {
            int ti = spTheme.getSelectedItemPosition();
            String mode = ti == 1 ? "light" : (ti == 2 ? "dark" : "system");
            prefs.setThemeMode(mode);
        }
        if (cbConfirmDrop != null) prefs.setConfirmDrop(cbConfirmDrop.isChecked());
        if (etMaxHistory != null) {
            try {
                prefs.setMaxQueryHistory(Integer.parseInt(etMaxHistory.getText().toString().trim()));
            } catch (Exception e) {
                Toast.makeText(this, R.string.invalid_history_count, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        int pos = spLanguage.getSelectedItemPosition();
        if (pos < 0 || pos >= LANG_CODES.length) pos = 0;
        prefs.setLanguage(LANG_CODES[pos]);

        boolean langChanged = !oldLang.equals(prefs.getLanguage());
        boolean themeChanged = !oldTheme.equals(prefs.getThemeMode());

        if (themeChanged) {
            AppCompatDelegate.setDefaultNightMode(AppPrefs.nightModeFor(prefs.getThemeMode()));
        }

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();

        Intent data = new Intent();
        data.putExtra(EXTRA_NEED_RECREATE, langChanged || themeChanged);
        setResult(RESULT_OK, data);
        // Return to the screen that opened Settings (home or database)
        finish();
    }
}
