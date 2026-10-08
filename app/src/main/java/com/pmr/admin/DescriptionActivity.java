package com.pmr.admin;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/* Экран «ОПИСАНИЕ» Android Link PMR V4.0.1.
 *
 * Читает файл res/raw/description.txt и показывает его в прокрутке.
 * Кнопка «ЗАКРЫТЬ» — возвращает на главный экран.
 */
public class DescriptionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_description);

        TextView textView = findViewById(R.id.descriptionText);
        Button closeBtn = findViewById(R.id.btnDescriptionClose);

        if (textView != null) {
            String text = readDescription();
            textView.setText(text);
        }

        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> finish());
        }
    }

    /* Чтение текста описания из res/raw/description.txt. */
    private String readDescription() {
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
            AppLog.add("DescriptionActivity: ошибка чтения описания — " + e);
            sb.append("Ошибка чтения файла описания: ").append(e);
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}