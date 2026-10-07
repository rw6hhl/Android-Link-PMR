package com.pmr.admin;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/* Главный экран Android Link PMR V2.5.1.
 *
 * Изменения V2.5.1:
 *   - при старте приложение запрашивает разрешение на доступ к CM108
 *     (без разрешения Cm108PttController не может управлять GPIO3);
 *   - после получения разрешения — при следующем запуске PmrService
 *     Cm108PttController.init() сработает успешно.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 100;
    private static final int REQ_MIC = 101;

    /* VID:PID CM108 (Digirig Lite). */
    private static final int CM108_VID = 0x0D8C;
    private static final int CM108_PID = 0x0012;

    /* Действие для PendingIntent запроса разрешения USB. */
    private static final String ACTION_USB_PERMISSION =
            "com.pmr.admin.USB_PERMISSION";

    private BroadcastReceiver usbReceiver = null;

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
        requestUsbPermissionIfNeeded();
        startServiceSafe();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbReceiver != null) {
            try { unregisterReceiver(usbReceiver); } catch (Exception ignored) {}
            usbReceiver = null;
        }
    }

    /* Запрос разрешения на доступ к CM108 по USB. */
    private void requestUsbPermissionIfNeeded() {
        try {
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                AppLog.add("MainActivity: UsbManager == null");
                return;
            }

            /* Ищем CM108 в списке устройств. */
            UsbDevice target = null;
            for (UsbDevice d : usbManager.getDeviceList().values()) {
                if (d.getVendorId() == CM108_VID
                        && d.getProductId() == CM108_PID) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                AppLog.add("MainActivity: CM108 не найден — разрешение не требуется");
                return;
            }
            if (usbManager.hasPermission(target)) {
                AppLog.add("MainActivity: разрешение на CM108 уже есть");
                return;
            }

            /* Регистрируем BroadcastReceiver для ответа. */
            usbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_USB_PERMISSION.equals(action)) {
                        boolean granted = intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        AppLog.add("MainActivity: USB permission granted="
                                + granted);
                    }
                }
            };

            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                /* Android 13+: требуется флаг RECEIVER_NOT_EXPORTED. */
                registerReceiver(usbReceiver, filter,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(usbReceiver, filter);
            }

            /* PendingIntent — для Android 12+ нужен FLAG_MUTABLE. */
            int flags = PendingIntent.FLAG_MUTABLE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_UPDATE_CURRENT;
            } else {
                flags |= PendingIntent.FLAG_UPDATE_CURRENT;
            }

            Intent permIntent = new Intent(ACTION_USB_PERMISSION);
            permIntent.setPackage(getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, permIntent, flags);

            usbManager.requestPermission(target, pi);
            AppLog.add("MainActivity: запрос разрешения на CM108 (VID=0x"
                    + Integer.toHexString(CM108_VID) + ", PID=0x"
                    + Integer.toHexString(CM108_PID) + ")");
        } catch (Exception e) {
            AppLog.add("MainActivity: requestUsbPermission FAIL — " + e);
        }
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