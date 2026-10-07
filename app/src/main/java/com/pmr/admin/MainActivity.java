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

/* Главный экран Android Link PMR V2.1.
 *
 * Изменения V2.1:
 *   - кнопка btnLogs заменена на btnUpdateCheck — проверка обновления;
 *   - обработка нажатия — вызов UpdateChecker.checkAndUpdate().
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

        Button btnUpdateCheck = findViewById(R.id.btnUpdateCheck);
        if (btnUpdateCheck != null) {
            btnUpdateCheck.setOnClickListener(v -> {
                String version = getString(R.string.app_version);
                if (version.startsWith("V")) version = version.substring(1);
                new UpdateChecker(MainActivity.this).checkAndUpdate(version);
            });
        }

        Button btnCommutation = findViewById(R.id.btnCommutation);
        if (btnCommutation != null) {
            btnCommutation.setOnClickListener(v -> openCommutation());
        }

        requestMicPermission();
        requestNotifPermission();
        startServiceSafe();
    }

    /* Открыть картинку GPIO USB Audio CM108.jpg. */
    private void openCommutation() {
        try {
            java.io.File dir = new java.io.File(getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, "gpio_usb_audio_cm108.jpg");

            java.io.InputStream is = getResources().openRawResource(
                    R.drawable.gpio_usb_audio_cm108);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", out);

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "image/jpeg");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            AppLog.add("openCommutation error: " + e);
            android.widget.Toast.makeText(this,
                    "Не удалось открыть файл: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
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