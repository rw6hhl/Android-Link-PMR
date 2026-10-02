package com.pmr.admin;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/* Главный экран Android Link PMR V1.7.
 *
 * Изменения V1.7:
 *   - добавлена кнопка «КОММУТАЦИЯ» под НАСТРОЙКИ и ЛОГИ;
 *   - по нажатию открывается картинка GPIO USB Audio CM108.jpg.
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

        Button btnCommutation = findViewById(R.id.btnCommutation);
        if (btnCommutation != null) {
            btnCommutation.setOnClickListener(v -> openCommutation());
        }

        requestMicPermission();
        requestNotifPermission();
        startServiceSafe();
    }

    /* Открыть картинку GPIO USB Audio CM108.jpg.
     * Картинка лежит в res/drawable/gpio_usb_audio_cm108.jpg.
     * Копируем её во временный файл и открываем через Intent.ACTION_VIEW. */
    private void openCommutation() {
        try {
            File dir = new File(getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "gpio_usb_audio_cm108.jpg");

            InputStream is = getResources().openRawResource(
                    R.drawable.gpio_usb_audio_cm108);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();

            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", out);

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "image/jpeg");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            AppLog.add("openCommutation error: " + e);
            Toast.makeText(this,
                    "Не удалось открыть файл: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
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