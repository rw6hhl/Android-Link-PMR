package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/* Экран логов Android Link PMR V4.0-BETA.
 *
 * Изменения V4.0-BETA:
 *   - убрана кнопка СОХРАНИТЬ В ФАЙЛ;
 *   - кнопка ОТПРАВИТЬ НА <email> — email из настроек (KEY_LOG_EMAIL);
 *   - кнопка ПОДЕЛИТЬСЯ — через Intent.ACTION_SEND.
 */
public class LogActivity extends AppCompatActivity {

    private TextView logText;
    private ScrollView logScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_log);

        logText = findViewById(R.id.logText);
        logScroll = findViewById(R.id.logScroll);

        Button btnClear = findViewById(R.id.btnClear);
        if (btnClear != null) btnClear.setOnClickListener(v -> {
            AppLog.clear();
            refreshLog();
        });

        Button btnShare = findViewById(R.id.btnShare);
        if (btnShare != null) btnShare.setOnClickListener(v -> shareLog());

        Button btnMail = findViewById(R.id.btnMail);
        if (btnMail != null) btnMail.setOnClickListener(v -> sendLogByMail());

        refreshLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLog();
    }

    private void refreshLog() {
        if (logText == null) return;
        String text = AppLog.getAll();
        logText.setText(text);
        if (logScroll != null) {
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    /* Поделиться логом через общий механизм ACTION_SEND. */
    private void shareLog() {
        String text = AppLog.getAll();
        if (text == null || text.isEmpty()) text = "(лог пуст)";
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Android Link PMR — Логи");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Поделиться логом"));
    }

    /* Отправить лог на email из настроек. */
    private void sendLogByMail() {
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);
        String email = sp.getString(PasswordActivity.KEY_LOG_EMAIL,
                PasswordActivity.DEFAULT_LOG_EMAIL);

        String text = AppLog.getAll();
        if (text == null || text.isEmpty()) text = "(лог пуст)";

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{ email });
        i.putExtra(Intent.EXTRA_SUBJECT, "Android Link PMR — Логи");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Отправить лог на " + email));
    }
}