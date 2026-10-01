package com.pmr.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/* VOX-движок Android Link PMR V1.0.
 *
 * Задача: автоматически определять наличие звука с микрофона и
 * отправлять его на сервер через PmrSocket — без кнопки PTT.
 *
 * Логика:
 *   - слушает микрофон непрерывно;
 *   - считает RMS-амплитуду каждого буфера PCM 16 бит;
 *   - если RMS >= My_Vox — начинается передача (startSending);
 *   - если RMS < My_Vox и держится тихо дольше vox_pause (в единицах по 20 мс) —
 *     передача останавливается (stopSending).
 *
 * Параметры (в SharedPreferences):
 *   My_Vox    = 0..500, 0 = VOX выключен;
 *   vox_pause = 1..500, длительность тишины до остановки передачи.
 */
public class VoxEngine {

    private static final int SAMPLE_RATE = 16000;
    private static final int BUF_ELEMENTS = 320;                 // 20 мс @ 16 кГц
    private static final int BYTES_PER_ELEM = 2;
    private static final int BUF_SIZE = BUF_ELEMENTS * BYTES_PER_ELEM;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioRecord recorder = null;
    private Thread voxThread = null;
    private volatile boolean running = false;

    /* Текущее состояние VOX. */
    private volatile boolean txActive = false;
    private volatile int lastRms = 0;

    public VoxEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isTxActive() { return txActive; }
    public int getLastRms()     { return lastRms; }

    /* Запуск VOX-потока. */
    public void start() {
        if (running) return;

        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUF_SIZE * 2);
            recorder.startRecording();
        } catch (Exception e) {
            AppLog.add("VoxEngine: ошибка AudioRecord — " + e);
            recorder = null;
            return;
        }

        running = true;
        voxThread = new Thread(this::loop, "vox-loop");
        voxThread.start();
        AppLog.add("VoxEngine: поток запущен");
    }

    public void stop() {
        running = false;
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception ignored) {}
            recorder = null;
        }
        voxThread = null;
        AppLog.add("VoxEngine: поток остановлен");
    }

    /* Главный цикл — читает микрофон, считает RMS, управляет передачей. */
    private void loop() {
        byte[] pcm16 = new byte[BUF_SIZE];
        byte[] g711buf = new byte[BUF_ELEMENTS];
        int silentTicks = 0;

        while (running) {
            int read;
            try {
                read = recorder.read(pcm16, 0, pcm16.length);
            } catch (Exception e) {
                break;
            }
            if (read <= 0) continue;

            int rms = calcRms(pcm16, read);
            lastRms = rms;

            /* Читаем настройки VOX из SharedPreferences. */
            SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            int myVox = sp.getInt(PasswordActivity.KEY_MY_VOX,
                    PasswordActivity.DEFAULT_MY_VOX);
            int voxPause = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                    PasswordActivity.DEFAULT_VOX_PAUSE);

            /* VOX выключен — не передаём. */
            if (myVox <= 0) {
                if (txActive) {
                    txActive = false;
                    AppLog.add("VoxEngine: VOX off");
                }
                silentTicks = 0;
                continue;
            }

            if (rms >= myVox) {
                /* Сигнал выше порога — передача. */
                silentTicks = 0;
                if (!txActive) {
                    txActive = true;
                    AppLog.add("VoxEngine: TX ON (rms=" + rms
                            + ", vox=" + myVox + ")");
                }
                sendFrame(pcm16, read, g711buf, myVox);
            } else {
                /* Сигнал ниже порога — считаем тишину. */
                if (txActive) {
                    silentTicks++;
                    /* vox_pause: 50 = 1 секунда. Один тик = 20 мс.
                     * Значит порог в тиках = vox_pause * 20 мс / 20 мс = vox_pause. */
                    if (silentTicks >= voxPause) {
                        txActive = false;
                        AppLog.add("VoxEngine: TX OFF (silent="
                                + silentTicks + ", pause=" + voxPause + ")");
                        silentTicks = 0;
                    } else {
                        /* Продолжаем отправлять тишину, чтобы не рвать поток. */
                        sendFrame(pcm16, read, g711buf, myVox);
                    }
                }
            }
        }
    }

    /* Отправка одного кадра G711 на сервер. */
    private void sendFrame(byte[] pcm16, int read, byte[] g711buf, int myVox) {
        if (pmrSocket == null) return;

        g711.encode(pcm16, 0, read, g711buf);
        int payloadLen = read / 2;

        int secret = pmrSocket.getKanalSecretInstance();

        /* Резервный пакет 326 байт: [cmd][kanal][client_lo][client_hi][secret_lo][secret_hi][payload] */
        byte[] reserve = new byte[6 + payloadLen];
        reserve[0] = (byte) AudioEngine.CMD_G711_16K;
        reserve[1] = 0;
        reserve[2] = (byte) (PmrSocket.Priznak_pmr & 0xFF);
        reserve[3] = (byte) ((PmrSocket.Priznak_pmr >> 8) & 0xFF);
        reserve[4] = (byte) (secret & 0xFF);
        reserve[5] = (byte) ((secret >> 8) & 0xFF);
        System.arraycopy(g711buf, 0, reserve, 6, payloadLen);

        /* Основной пакет 324 байта — без secret. */
        byte[] main = new byte[4 + payloadLen];
        main[0] = (byte) AudioEngine.CMD_G711_16K;
        main[1] = 0;
        main[2] = (byte) (PmrSocket.Priznak_pmr & 0xFF);
        main[3] = (byte) ((PmrSocket.Priznak_pmr >> 8) & 0xFF);
        System.arraycopy(g711buf, 0, main, 4, payloadLen);

        pmrSocket.sendVoice(main, reserve);
    }

    /* RMS-амплитуда буфера PCM 16 бит (little-endian). */
    private static int calcRms(byte[] pcm, int len) {
        long sum = 0;
        int n = len / 2;
        for (int i = 0; i < n; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            sum += (long) s * s;
        }
        if (n == 0) return 0;
        double mean = (double) sum / n;
        double rms = Math.sqrt(mean);
        /* Масштабируем в 0..500 — как ожидает UI. */
        int scaled = (int) (rms / 64.0);
        if (scaled > 500) scaled = 500;
        return scaled;
    }
}