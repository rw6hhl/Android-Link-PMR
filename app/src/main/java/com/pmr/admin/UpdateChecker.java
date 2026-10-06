package com.pmr.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* Проверка обновлений Android Link PMR V1.9.
 *
 * Логика:
 *   - по текущей версии строит имя следующей версии APK;
 *   - делает HEAD-запрос на https://github.com/rw6hhl/Android-Link-PMR/raw/main/apk/<file>;
 *   - если 200 OK — показывает диалог «Скачать?»;
 *   - при согласии — скачивает APK в папку Download;
 *   - показывает диалог-подсказку «установите вручную».
 *
 * Логика перехода версий:
 *   V1.9 → V2.0 (major + 1, minor = 0)
 *   V1.5 → V1.6 (minor + 1)
 */
public class UpdateChecker {

    /* URL репозитория, где лежит папка apk/ с релизами. */
    public static final String BASE_URL =
            "https://github.com/rw6hhl/Android-Link-PMR/raw/main/apk/";

    private final Context ctx;

    public UpdateChecker(Context ctx) {
        this.ctx = ctx;
    }

    /* Точка входа: version — строка вида "1.9" (без V). */
    public void checkAndUpdate(String version) {
        String fileName = buildNextVersionName(version);
        if (fileName == null) {
            Toast.makeText(ctx, "Не удалось определить версию",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        new CheckTask().execute(fileName);
    }

    /* Строит имя следующего APK. V1.9 → "app-v2_0.apk". */
    private String buildNextVersionName(String current) {
        try {
            String v = current.trim();
            if (v.startsWith("V")) v = v.substring(1);
            int dot = v.indexOf('.');
            if (dot <= 0) return null;
            int major = Integer.parseInt(v.substring(0, dot));
            int minor = Integer.parseInt(v.substring(dot + 1));
            if (minor >= 9) {
                major++;
                minor = 0;
            } else {
                minor++;
            }
            return "app-v" + major + "_" + minor + ".apk";
        } catch (Exception e) {
            return null;
        }
    }

    /* Проверка наличия файла HEAD-запросом. */
    private class CheckTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String fileName = params[0];
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + fileName);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    return fileName;
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String fileName) {
            if (fileName == null) {
                Toast.makeText(ctx, "Обновлений нет",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            askDownload(fileName);
        }
    }

    /* Диалог: «Найдено обновление. Скачать?». */
    private void askDownload(final String fileName) {
        new AlertDialog.Builder(ctx)
                .setTitle("Найдено обновление")
                .setMessage("Доступна новая версия: " + fileName
                        + "\n\nСкачать?")
                .setPositiveButton("Скачать", (d, w) -> new DownloadTask().execute(fileName))
                .setNegativeButton("Отмена", null)
                .show();
    }

    /* Скачивание APK в папку Download. */
    private class DownloadTask extends AsyncTask<String, Void, File> {
        @Override
        protected File doInBackground(String... params) {
            String fileName = params[0];
            HttpURLConnection conn = null;
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                URL url = new URL(BASE_URL + fileName);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, fileName);

                is = conn.getInputStream();
                fos = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                return out;
            } catch (Exception e) {
                AppLog.add("UpdateChecker: ошибка скачивания — " + e);
                return null;
            } finally {
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(File apk) {
            if (apk == null) {
                Toast.makeText(ctx, "Ошибка скачивания",
                        Toast.LENGTH_LONG).show();
                return;
            }
            showInstallHint(apk);
        }
    }

    /* Диалог-подсказка после скачивания. */
    private void showInstallHint(File apk) {
        new AlertDialog.Builder(ctx)
                .setTitle("Файл сохранён")
                .setMessage("Путь: " + apk.getAbsolutePath()
                        + "\n\nКак установить:"
                        + "\n1. Удалите старую версию Android Link PMR."
                        + "\n2. Откройте проводник."
                        + "\n3. Перейдите в папку Download."
                        + "\n4. Найдите файл " + apk.getName() + "."
                        + "\n5. Нажмите на него и установите.")
                .setPositiveButton("Понятно", null)
                .show();
    }
}