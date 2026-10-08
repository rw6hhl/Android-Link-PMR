package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* Экран «ЛОГИ» Android Link PMR V4.0.1.
 *
 * Изменения V4.0.1:
 *   - убрана кнопка «СОХРАНИТЬ В ФАЙЛ»;
 *   - добавлено поле email для отправки лога;
 *   - добавлена кнопка EMAIL — отправка лога на указанный адрес;
 *   - кнопки ПОДЕЛИТЬСЯ и EMAIL формируют временный файл и отправляют через Intent;
 *   - используется AppLog.dump() вместо AppLog.get().
 */
public class LogActivity extends AppCompatActivity {

    private TextView logText;
    private EditText logEmailInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_log);

        logText = findViewById(R.id.logText);
        logEmailInput = findViewById(R.id.logEmailInput);
        Button btnClear = findViewById(R.id.btnClear);
        Button btnShare = findViewById(R.id.btnShare);
        Button btnSendEmail = findViewById(R.id.btnSendEmail);

        /* Загрузить email из SharedPreferences. */
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);
        String savedEmail = sp.getString(PasswordActivity.KEY_LOG_EMAIL,
                PasswordActivity.DEFAULT_LOG_EMAIL);
        if (logEmailInput != null) logEmailInput.setText(savedEmail);

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                AppLog.clear();
                refresh();
            });
        }

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareLog());
        }

        if (btnSendEmail != null) {
            btnSendEmail.setOnClickListener(v -> sendLogByEmail());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /* Обновить содержимое лога. */
    private void refresh() {
        if (logText != null) {
            logText.setText(AppLog.dump());
        }
    }

    /* Сохранить лог во временный файл и получить Uri. */
    private Uri writeLogToFile() {
        try {
            File dir = new File(getCacheDir(), "log");
            if (!dir.exists()) dir.mkdirs();

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.US).format(new Date());
            File out = new File(dir, "log_" + ts + ".txt");

            FileOutputStream fos = new FileOutputStream(out);
            fos.write(AppLog.dump().getBytes("UTF-8"));
            fos.close();

            return FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", out);
        } catch (Exception e) {
            AppLog.add("LogActivity: ошибка записи лога — " + e);
            return null;
        }
    }

    /* Поделиться логом. */
    private void shareLog() {
        Uri uri = writeLogToFile();
        if (uri == null) {
            Toast.makeText(this, R.string.log_save_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Поделиться логом"));
    }

    /* Отправить лог на email. */
    private void sendLogByEmail() {
        String to = (logEmailInput != null)
                ? logEmailInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(to)) {
            Toast.makeText(this, "Укажите email",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        /* Сохранить email в настройках. */
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);
        sp.edit().putString(PasswordActivity.KEY_LOG_EMAIL, to).apply();

        Uri uri = writeLogToFile();
        if (uri == null) {
            Toast.makeText(this, R.string.log_save_error,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        i.putExtra(Intent.EXTRA_SUBJECT, "Android Link PMR — лог");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Отправить лог"));
    }
}