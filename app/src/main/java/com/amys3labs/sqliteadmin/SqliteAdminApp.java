package com.amys3labs.sqliteadmin;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

public class SqliteAdminApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            AppPrefs prefs = new AppPrefs(this);
            AppCompatDelegate.setDefaultNightMode(
                    prefs.isDarkTheme()
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO);
        } catch (Throwable t) {
            // Never crash the process over theme prefs
            t.printStackTrace();
        }
    }
}
