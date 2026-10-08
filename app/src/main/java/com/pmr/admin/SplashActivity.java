package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/* Экран приветствия Android Link PMR V4.0.1.
 *
 * Изменения V4.0.1:
 *   - убран переход на CheckActivity (экран проверки системы удалён);
 *   - переход сразу на PasswordActivity или MainActivity;
 *   - splash показывается 5 секунд.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 5000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_splash);

        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);

        String call = sp.getString(PasswordActivity.KEY_CALLSIGN,
                PasswordActivity.DEFAULT_CALLSIGN);
        String qth = sp.getString(PasswordActivity.KEY_CITY,
                PasswordActivity.DEFAULT_CITY);

        TextView tvCall = findViewById(R.id.splashCall);
        if (tvCall != null) tvCall.setText("Call: " + call);

        TextView tvQth = findViewById(R.id.splashQth);
        if (tvQth != null) tvQth.setText("QTH: " + qth);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean requirePass = sp.getBoolean(
                    PasswordActivity.KEY_REQUIRE_PASSWORD, true);

            Intent i;
            if (requirePass) {
                i = new Intent(SplashActivity.this, PasswordActivity.class);
            } else {
                i = new Intent(SplashActivity.this, MainActivity.class);
            }
            startActivity(i);
            finish();
        }, SPLASH_DELAY_MS);
    }
}