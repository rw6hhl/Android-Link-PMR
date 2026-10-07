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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/* Проверка обновлений Android Link PMR V2.3.
 *
 * Изменения V2.3:
 *   - на Android 10+ (API 29) скачивание выполняется через MediaStore
 *     (прямая запись в Download/ запрещена scoped storage);
 *   - на Android ≤ 9 используется FileOutputStream в Download/
 *     (как раньше);
 *   - показ пути к сохранённому APK.
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

    /* Скачивание APK.
     * Android 10+ — через MediaStore.Downloads.
     * Android ≤ 9 — через FileOutputStream в Download/. */
    private class DownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String fileName = params[0];
            HttpURLConnection conn = null;
            InputStream is = null;
            OutputStream os = null;
            try {
                URL url = new URL(BASE_URL + fileName);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                is = conn.getInputStream();

                String savedPath;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    /* Android 10+ — через MediaStore. */
                    savedPath = saveViaMediaStore(fileName, is);
                } else {
                    /* Android ≤ 9 — старая схема. */
                    savedPath = saveViaFile(fileName, is);
                }
                return savedPath;
            } catch (Exception e) {
                AppLog.add("UpdateChecker: ошибка скачивания — " + e);
                return null;
            } finally {
                try { if (os != null) os.close(); } catch (Exception ignored) {}
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

    /* Android 10+ — MediaStore.Downloads. */
    private String saveViaMediaStore(String fileName, InputStream is) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) {
            throw new Exception("MediaStore: uri == null");
        }
        OutputStream os = cr.openOutputStream(uri);
        if (os == null) {
            throw new Exception("MediaStore: os == null");
        }
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        os.close();
        return Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
    }

    /* Android ≤ 9 — FileOutputStream в Download/. */
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

    /* Подсказка после скачивания APK — как установить. */
    private void showInstallHint(String path) {
        String msg = "Файл сохранён:\n" + path + "\n\n"
                + "Как установить:\n"
                + "1. Удалите старую версию Android Link PMR "
                + "(Настройки → Приложения → Android Link PMR → Удалить).\n"
                + "2. Откройте проводник (Files, Мои файлы).\n"
                + "3. Перейдите в папку Download.\n"
                + "4. Найдите скачанный APK.\n"
                + "5. Нажмите на него и установите.";

        new AlertDialog.Builder(ctx)
                .setTitle("Готово к установке")
                .setMessage(msg)
                .setPositiveButton("Понятно", null)
                .show();

        AppLog.add("UpdateChecker: подсказка показана, APK=" + path);
    }
}