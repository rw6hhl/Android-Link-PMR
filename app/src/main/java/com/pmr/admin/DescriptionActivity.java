package com.pmr.admin;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/* Экран описания работы программы Android Link PMR V4.0-BETA.
 *
 * Читает текстовый файл из res/raw/description.txt и показывает
 * его в прокручиваемом TextView. Кнопка ЗАКРЫТЬ завершает активность.
 */
public class DescriptionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_description);

        TextView descriptionText = findViewById(R.id.descriptionText);
        if (descriptionText != null) {
            descriptionText.setText(loadDescription());
        }

        Button closeBtn = findViewById(R.id.btnCloseDescription);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> finish());
        }
    }

    /* Чтение описания из res/raw/description.txt. */
    private String loadDescription() {
        StringBuilder sb = new StringBuilder();
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = getResources().openRawResource(R.raw.description);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            AppLog.add("DescriptionActivity: ошибка чтения — " + e);
            sb.setLength(0);
            sb.append("Не удалось загрузить описание.\n");
            sb.append("Ошибка: ").append(e.toString());
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}