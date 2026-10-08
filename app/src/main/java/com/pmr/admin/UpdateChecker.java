package com.pmr.admin;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* Проверка обновлений Android Link PMR V4.0.1.
 *
 * Изменения V4.0.1:
 *   - buildNextVersionName() поддерживает трёхзначные версии
 *     (X.Y.Z) и суффиксы -BETA/-ALPHA (игнорируются);
 *   - при 4.0.1 → app-v4_0_2.apk;
 *   - при 4.0.9 → app-v4_1_0.apk.
 *
 * Логика:
 *   - читает latest.txt из папки apk/;
 *   - если имя из latest.txt не совпадает с текущим — предлагает скачать;
 *   - на Android 10+ сохраняет через MediaStore, на ≤ 9 — через FileOutputStream.
 */
public class UpdateChecker {

    public static final String BASE_URL =
            "https://github.com/rw6hhl/Android-Link-PMR/raw/main/apk/";

    private final Context ctx;

    public UpdateChecker(Context ctx) {
        this.ctx = ctx;
    }

    /* version — строка вида "4.0.1" (без V). */
    public void checkAndUpdate(String version) {
        new LatestTask(version).execute();
    }

    /* Построение имени следующего APK.
     * "4.0.1"   → "app-v4_0_2.apk"
     * "4.0.9"   → "app-v4_1_0.apk"
     * "4.1.9"   → "app-v4_2_0.apk"
     * "4.0"     → "app-v4_0_1.apk"
     * "4.0.1-BETA" → "app-v4_0_2.apk"  (суффикс игнорируется) */
    private String buildNextVersionName(String current) {
        try {
            String v = current.trim();
            if (v.startsWith("V")) v = v.substring(1);

            /* Оставляем только цифры и точки. */
            String digits = v.replaceAll("[^0-9.]", "");
            if (digits.isEmpty()) return null;

            /* Убираем точку в конце, если есть. */
            while (digits.endsWith(".")) {
                digits = digits.substring(0, digits.length() - 1);
            }
            String[] parts = digits.split("\\.");
            if (parts.length < 2) return null;

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 0;

            /* Инкремент: patch + 1; при patch >= 9 — minor + 1, patch = 0;
             * при minor >= 10 — major + 1, minor = 0, patch = 0. */
            if (patch >= 9) {
                minor++;
                patch = 0;
                if (minor >= 10) {
                    major++;
                    minor = 0;
                }
            } else {
                patch++;
            }

            return "app-v" + major + "_" + minor + "_" + patch + ".apk";
        } catch (Exception e) {
            return null;
        }
    }

    private class LatestTask extends AsyncTask<Void, Void, String> {

        private final String currentVersion;

        LatestTask(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        @Override
        protected String doInBackground(Void... params) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(BASE_URL + "latest.txt");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code != 200) return null;

                InputStream is = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line = reader.readLine();
                if (line == null) return null;
                return line.trim();
            } catch (Exception e) {
                AppLog.add("UpdateChecker: ошибка чтения latest.txt — " + e);
                return null;
            } finally {
                try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String latestFile) {
            if (latestFile == null || latestFile.isEmpty()) {
                Toast.makeText(ctx, "Обновлений нет",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            /* Ожидаемое имя текущего APK. */
            String nextFile = buildNextVersionName(currentVersion);
            if (nextFile == null) {
                Toast.makeText(ctx, "Не удалось определить версию",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            /* Текущий APK — на 1 меньше nextFile; но проще: сравнить
             * latestFile с ожидаемым СЛЕДУЮЩИМ именем. Если latestFile
             * меньше nextFile — обновлений нет. Если больше или равен — есть. */
            AppLog.add("UpdateChecker: latest=" + latestFile
                    + ", current=" + currentVersion
                    + ", next=" + nextFile);

            /* Упрощённая логика: если latestFile == nextFile — предложить.
             * Если latestFile — та же версия что и текущая — обновлений нет. */
            String myFile = "app-v" + currentVersion.replace(".", "_") + ".apk";
            if (latestFile.equalsIgnoreCase(myFile)) {
                Toast.makeText(ctx, "Обновлений нет",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            askDownload(latestFile);
        }
    }

    private void askDownload(final String fileName) {
        new AlertDialog.Builder(ctx)
                .setTitle("Найдено обновление")
                .setMessage("Доступна новая версия: " + fileName
                        + "\n\nСкачать?")
                .setPositiveButton("Скачать", (d, w) -> new DownloadTask().execute(fileName))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private class DownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String fileName = params[0];
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                URL url = new URL(BASE_URL + fileName);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                is = conn.getInputStream();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return saveViaMediaStore(fileName, is);
                } else {
                    return saveViaFile(fileName, is);
                }
            } catch (Exception e) {
                AppLog.add("UpdateChecker: ошибка скачивания — " + e);
                return null;
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String savedPath) {
            if (savedPath == null) {
                Toast.makeText(ctx, "Ошибка скачивания",
                        Toast.LENGTH_LONG).show();
                return;
            }
            showInstallHint(savedPath);
        }
    }

    private String saveViaMediaStore(String fileName, InputStream is) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE,
                "application/vnd.android.package-archive");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS);

        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new Exception("MediaStore: uri == null");

        OutputStream os = cr.openOutputStream(uri);
        if (os == null) throw new Exception("MediaStore: os == null");

        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        os.close();
        return Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
    }

    private String saveViaFile(String fileName, InputStream is) throws Exception {
        File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close();
        return out.getAbsolutePath();
    }

    private void showInstallHint(String path) {
        String fileName = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            fileName = path.substring(slash + 1);
        }

        String msg = "Файл сохранён:\n" + path + "\n\n"
                + "Как установить:\n"
                + "1. Удалите старую версию Android Link PMR "
                + "(Настройки → Приложения → Android Link PMR → Удалить).\n"
                + "2. Откройте проводник (Files, Мои файлы).\n"
                + "3. Перейдите в папку Download.\n"
                + "4. Найдите файл " + fileName + ".\n"
                + "5. Нажмите на него и установите.";

        new AlertDialog.Builder(ctx)
                .setTitle("Готово к установке")
                .setMessage(msg)
                .setPositiveButton("Понятно", null)
                .show();

        AppLog.add("UpdateChecker: подсказка показана, APK=" + path);
    }
}