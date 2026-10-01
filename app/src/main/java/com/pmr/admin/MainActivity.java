package com.pmr.admin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/* Главный экран Android Link PMR V1.0.
 *
 * Задача: запустить foreground-службу, дать кнопки «НАСТРОЙКИ» и «ЛОГИ».
 * Список абонентов, PTT, индикаторы — удалены.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 100;
    private static final int REQ_MIC = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        Button btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            });
        }

        Button btnLogs = findViewById(R.id.btnLogs);
        if (btnLogs != null) {
            btnLogs.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, LogActivity.class);
                startActivity(i);
            });
        }

        requestMicPermission();
        requestNotifPermission();
        startServiceSafe();
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF);
            }
        }
    }

    private void startServiceSafe() {
        Intent svc = new Intent(this, PmrService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }
}