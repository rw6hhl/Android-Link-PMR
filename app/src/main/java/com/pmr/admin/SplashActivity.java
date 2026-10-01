package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

/* Экран приветствия Android Link PMR V1.0.
 * Показывается 5 секунд.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 5000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences sp = getSharedPreferences(
                    PasswordActivity.PREFS, MODE_PRIVATE);

            boolean checkSystem = sp.getBoolean(
                    PasswordActivity.KEY_CHECK_SYSTEM, true);

            Intent i;
            if (checkSystem) {
                i = new Intent(SplashActivity.this, CheckActivity.class);
            } else {
                boolean requirePass = sp.getBoolean(
                        PasswordActivity.KEY_REQUIRE_PASSWORD, true);
                if (requirePass) {
                    i = new Intent(SplashActivity.this, PasswordActivity.class);
                } else {
                    i = new Intent(SplashActivity.this, MainActivity.class);
                }
            }
            startActivity(i);
            finish();
        }, SPLASH_DELAY_MS);
    }
}