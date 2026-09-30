package com.amys3labs.sqliteadmin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Central app settings + recent database list.
 */
public class AppPrefs {

    private static final String PREFS = "sqlite_admin_prefs";

    public static final String KEY_PAGE_SIZE = "page_size";
    public static final String KEY_RECENT_COUNT = "recent_count";
    public static final String KEY_AUTO_SAVE = "auto_save";
    public static final String KEY_CONFIRM_SQL = "confirm_destructive_sql";
    public static final String KEY_DARK_THEME = "dark_theme"; // legacy
    public static final String KEY_THEME_MODE = "theme_mode"; // system|light|dark
    private static final String KEY_FAVORITES = "query_favorites";
    public static final String KEY_LANGUAGE = "language";
    private static final String KEY_QUERY_HISTORY = "query_history";
    private static final String KEY_MAX_QUERY_HISTORY = "max_query_history";
    private static final String KEY_CONFIRM_DROP = "confirm_drop";
    public static final int DEFAULT_MAX_QUERY_HISTORY = 40;
    private static final String KEY_RECENT = "recent_dbs";

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int DEFAULT_RECENT_COUNT = 8;

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getPageSize() {
        int v = sp.getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE);
        if (v < 10) v = 10;
        if (v > 5000) v = 5000;
        return v;
    }

    public void setPageSize(int v) {
        if (v < 10) v = 10;
        if (v > 5000) v = 5000;
        sp.edit().putInt(KEY_PAGE_SIZE, v).apply();
    }

    public int getRecentCount() {
        int v = sp.getInt(KEY_RECENT_COUNT, DEFAULT_RECENT_COUNT);
        if (v < 0) v = 0;
        if (v > 30) v = 30;
        return v;
    }

    public void setRecentCount(int v) {
        if (v < 0) v = 0;
        if (v > 30) v = 30;
        sp.edit().putInt(KEY_RECENT_COUNT, v).apply();
    }

    public boolean isAutoSave() {
        return sp.getBoolean(KEY_AUTO_SAVE, false);
    }

    public void setAutoSave(boolean v) {
        sp.edit().putBoolean(KEY_AUTO_SAVE, v).apply();
    }

    public boolean isConfirmDestructiveSql() {
        return sp.getBoolean(KEY_CONFIRM_SQL, true);
    }

    public void setConfirmDestructiveSql(boolean v) {
        sp.edit().putBoolean(KEY_CONFIRM_SQL, v).apply();
    }

    /** @deprecated use getThemeMode */
    public boolean isDarkTheme() {
        return "dark".equals(getThemeMode());
    }

    public void setDarkTheme(boolean v) {
        setThemeMode(v ? "dark" : "light");
    }

    /** system | light | dark */
    public String getThemeMode() {
        String m = sp.getString(KEY_THEME_MODE, null);
        if (m == null || m.isEmpty()) {
            // migrate legacy
            m = sp.getBoolean(KEY_DARK_THEME, false) ? "dark" : "system";
        }
        if (!"system".equals(m) && !"light".equals(m) && !"dark".equals(m)) {
            m = "system";
        }
        return m;
    }

    public void setThemeMode(String mode) {
        if (mode == null) mode = "system";
        if (!"system".equals(mode) && !"light".equals(mode) && !"dark".equals(mode)) {
            mode = "system";
        }
        sp.edit().putString(KEY_THEME_MODE, mode)
                .putBoolean(KEY_DARK_THEME, "dark".equals(mode))
                .apply();
    }

    public static int nightModeFor(String themeMode) {
        if ("dark".equals(themeMode)) {
            return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
        }
        if ("light".equals(themeMode)) {
            return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        }
        return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    public java.util.List<String[]> getFavorites() {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(sp.getString(KEY_FAVORITES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                list.add(new String[]{o.optString("name", ""), o.optString("sql", "")});
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveFavorites(java.util.List<String[]> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String[] pair : list) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("name", pair[0]);
                o.put("sql", pair[1]);
                arr.put(o);
            }
            sp.edit().putString(KEY_FAVORITES, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void addFavorite(String name, String sql) {
        if (name == null || sql == null) return;
        name = name.trim();
        sql = sql.trim();
        if (name.isEmpty() || sql.isEmpty()) return;
        java.util.List<String[]> list = getFavorites();
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] p : list) {
            if (!name.equals(p[0])) out.add(p);
        }
        out.add(0, new String[]{name, sql});
        while (out.size() > 50) out.remove(out.size() - 1);
        saveFavorites(out);
    }

    public void removeFavorite(String name) {
        java.util.List<String[]> list = getFavorites();
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String[] p : list) {
            if (!name.equals(p[0])) out.add(p);
        }
        saveFavorites(out);
    }

    /** "", "en", "pt", "es", "de" — empty means system default */
    public String getLanguage() {
        return sp.getString(KEY_LANGUAGE, "");
    }

    public void setLanguage(String code) {
        if (code == null) code = "";
        sp.edit().putString(KEY_LANGUAGE, code).apply();
    }

    public int getMaxQueryHistory() {
        int v = sp.getInt(KEY_MAX_QUERY_HISTORY, DEFAULT_MAX_QUERY_HISTORY);
        if (v < 5) v = 5;
        if (v > 100) v = 100;
        return v;
    }

    public void setMaxQueryHistory(int v) {
        if (v < 5) v = 5;
        if (v > 100) v = 100;
        sp.edit().putInt(KEY_MAX_QUERY_HISTORY, v).apply();
    }

    public boolean isConfirmDrop() {
        return sp.getBoolean(KEY_CONFIRM_DROP, true);
    }

    public void setConfirmDrop(boolean v) {
        sp.edit().putBoolean(KEY_CONFIRM_DROP, v).apply();
    }

    public java.util.List<String> getQueryHistory() {
        java.util.List<String> list = new java.util.ArrayList<>();
        String raw = sp.getString(KEY_QUERY_HISTORY, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (!s.isEmpty()) list.add(s);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void addQueryHistory(String sql) {
        if (sql == null) return;
        sql = sql.trim();
        if (sql.isEmpty()) return;
        java.util.List<String> list = getQueryHistory();
        // dedupe: remove same query
        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (String s : list) {
            if (!sql.equals(s)) filtered.add(s);
        }
        filtered.add(0, sql);
        int max = getMaxQueryHistory();
        while (filtered.size() > max) {
            filtered.remove(filtered.size() - 1);
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (String s : filtered) arr.put(s);
            sp.edit().putString(KEY_QUERY_HISTORY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void clearQueryHistory() {
        sp.edit().putString(KEY_QUERY_HISTORY, "[]").apply();
    }

    public static class RecentEntry {
        public String uri;
        public String name;
        public long openedAt;

        public RecentEntry(String uri, String name, long openedAt) {
            this.uri = uri;
            this.name = name;
            this.openedAt = openedAt;
        }
    }

    public List<RecentEntry> getRecent() {
        List<RecentEntry> list = new ArrayList<>();
        String raw = sp.getString(KEY_RECENT, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new RecentEntry(
                        o.optString("uri", ""),
                        o.optString("name", "database"),
                        o.optLong("openedAt", 0)
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void addRecent(String uri, String name) {
        if (uri == null || uri.isEmpty()) return;
        List<RecentEntry> list = getRecent();
        // remove duplicate uri
        List<RecentEntry> filtered = new ArrayList<>();
        for (RecentEntry e : list) {
            if (!uri.equals(e.uri)) filtered.add(e);
        }
        filtered.add(0, new RecentEntry(uri, name != null ? name : "database", System.currentTimeMillis()));
        int max = getRecentCount();
        if (max <= 0) {
            sp.edit().putString(KEY_RECENT, "[]").apply();
            return;
        }
        while (filtered.size() > max) {
            filtered.remove(filtered.size() - 1);
        }
        saveRecent(filtered);
    }

    public void clearRecent() {
        sp.edit().putString(KEY_RECENT, "[]").apply();
    }

    private void saveRecent(List<RecentEntry> list) {
        try {
            JSONArray arr = new JSONArray();
            for (RecentEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("uri", e.uri);
                o.put("name", e.name);
                o.put("openedAt", e.openedAt);
                arr.put(o);
            }
            sp.edit().putString(KEY_RECENT, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
