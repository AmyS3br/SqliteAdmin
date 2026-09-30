package com.amys3labs.sqliteadmin;

import android.content.Context;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView tvVersion = findViewById(R.id.tvVersion);
        String ver = "1.0";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        tvVersion.setText(getString(R.string.about_version, ver));

        TextView body = findViewById(R.id.tvAboutBody);
        body.setSingleLine(false);
        body.setText(getString(R.string.about_body,
                getString(R.string.footer_email),
                getString(R.string.footer_grok)));
        body.setAutoLinkMask(Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
        body.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
