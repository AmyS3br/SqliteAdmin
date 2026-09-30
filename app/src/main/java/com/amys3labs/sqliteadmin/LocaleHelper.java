package com.amys3labs.sqliteadmin;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {}

    public static Context apply(Context context) {
        try {
            if (context == null) return context;
            String code = new AppPrefs(context).getLanguage();
            if (code == null || code.isEmpty()) {
                return context;
            }
            Locale locale = new Locale(code);
            Locale.setDefault(locale);
            Configuration config = new Configuration(context.getResources().getConfiguration());
            if (Build.VERSION.SDK_INT >= 24) {
                config.setLocale(locale);
                return context.createConfigurationContext(config);
            } else if (Build.VERSION.SDK_INT >= 17) {
                config.setLocale(locale);
                return context.createConfigurationContext(config);
            } else {
                config.locale = locale;
                Resources res = context.getResources();
                res.updateConfiguration(config, res.getDisplayMetrics());
                return context;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return context;
        }
    }
}
