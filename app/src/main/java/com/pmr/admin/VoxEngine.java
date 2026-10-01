package com.pmr.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/* VOX-движок Android Link PMR V1.2.
 *
 * Изменения V1.2:
 *   - при смене USB-аудио вызывается rebuildRecorder();
 *   - добавлен heartbeat — если read() не возвращает данные 3 секунды,
 *     поток пересоздаёт AudioRecord (лечит зависание при USB).
 */
public class VoxEngine {

    private static final int SAMPLE_RATE = 16000;
    private static final int BUF_ELEMENTS = 320;
    private static final int BYTES_PER_ELEM = 2;
    private static final int BUF_SIZE = BUF_ELEMENTS * BYTES_PER_ELEM;

    private static final long HEARTBEAT_TIMEOUT_MS = 3000L;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioRecord recorder = null;
    private Thread voxThread = null;
    private volatile boolean running = false;
    private volatile long lastReadTime = 0L;

    private volatile boolean txActive = false;
    private volatile int lastRms = 0;

    public VoxEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isTxActive() { return txActive; }
    public int getLastRms()     { return lastRms; }

    public void start() {
        if (running) return;
        if (!openRecorder()) return;

        running = true;
        voxThread = new Thread(this::loop, "vox-loop");
        voxThread.start();
        AppLog.add("VoxEngine: поток запущен");
    }

    public void stop() {
        running = false;
        closeRecorder();
        voxThread = null;
        AppLog.add("VoxEngine: поток остановлен");
    }

    /* Пересоздать AudioRecord при смене устройства. */
    public void rebuildRecorder() {
        if (!running) return;
        AppLog.add("VoxEngine: rebuildRecorder()");
        closeRecorder();
        openRecorder();
    }

    private boolean openRecorder() {
        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUF_SIZE * 2);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                AppLog.add("VoxEngine: AudioRecord STATE_INITIALIZED FAIL");
                recorder.release();
                recorder = null;
                return false;
            }
            recorder.startRecording();
            lastReadTime = System.currentTimeMillis();
            AppLog.add("VoxEngine: AudioRecord init OK");
            return true;
        } catch (Exception e) {
            AppLog.add("VoxEngine: ошибка AudioRecord — " + e);
            recorder = null;
            return false;
        }
    }

    private void closeRecorder() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception ignored) {}
            recorder = null;
        }
    }

    private void loop() {
        byte[] pcm16 = new byte[BUF_SIZE];
        byte[] g711buf = new byte[BUF_ELEMENTS];
        int silentTicks = 0;
        int diagCounter = 0;

        while (running) {
            /* Heartbeat — если давно не было данных, пересоздаём AudioRecord. */
            long now = System.currentTimeMillis();
            if (lastReadTime > 0 && (now - lastReadTime) > HEARTBEAT_TIMEOUT_MS) {
                AppLog.add("VoxEngine: heartbeat timeout — пересоздание AudioRecord");
                closeRecorder();
                openRecorder();
                lastReadTime = System.currentTimeMillis();
                continue;
            }

            int read;
            try {
                read = recorder.read(pcm16, 0, pcm16.length);
            } catch (Exception e) {
                AppLog.add("VoxEngine: read exception — " + e);
                closeRecorder();
                openRecorder();
                continue;
            }
            if (read > 0) {
                lastReadTime = System.currentTimeMillis();
            } else {
                continue;
            }

            int rms = calcRms(pcm16, read);
            lastRms = rms;

            SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            int myVox = sp.getInt(PasswordActivity.KEY_MY_VOX,
                    PasswordActivity.DEFAULT_MY_VOX);
            int voxPause = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                    PasswordActivity.DEFAULT_VOX_PAUSE);

            diagCounter++;
            if (diagCounter >= 50) {
                AppLog.add("VoxEngine: rms=" + rms + ", vox=" + myVox
                        + ", tx=" + txActive);
                diagCounter = 0;
            }

            if (myVox <= 0) {
                if (txActive) {
                    txActive = false;
                    AppLog.add("VoxEngine: VOX off (myVox=0)");
                }
                silentTicks = 0;
                continue;
            }

            if (rms >= myVox) {
                silentTicks = 0;
                if (!txActive) {
                    txActive = true;
                    AppLog.add("VoxEngine: TX ON (rms=" + rms
                            + ", vox=" + myVox + ")");
                }
                sendFrame(pcm16, read, g711buf);
            } else {
                if (txActive) {
                    silentTicks++;
                    if (silentTicks >= voxPause) {
                        txActive = false;
                        AppLog.add("VoxEngine: TX OFF (silent="
                                + silentTicks + ", pause=" + voxPause + ")");
                        silentTicks = 0;
                    } else {
                        sendFrame(pcm16, read, g711buf);
                    }
                }
            }
        }
    }

    private void sendFrame(byte[] pcm16, int read, byte[] g711buf) {
        if (pmrSocket == null) return;

        g711.encode(pcm16, 0, read, g711buf);
        int payloadLen = read / 2;
        int secret = pmrSocket.getKanalSecretInstance();

        byte[] reserve = new byte[6 + payloadLen];
        reserve[0] = (byte) AudioEngine.CMD_G711_16K;
        reserve[1] = 0;
        reserve[2] = (byte) (PmrSocket.Priznak_pmr & 0xFF);
        reserve[3] = (byte) ((PmrSocket.Priznak_pmr >> 8) & 0xFF);
        reserve[4] = (byte) (secret & 0xFF);
        reserve[5] = (byte) ((secret >> 8) & 0xFF);
        System.arraycopy(g711buf, 0, reserve, 6, payloadLen);

        byte[] main = new byte[4 + payloadLen];
        main[0] = (byte) AudioEngine.CMD_G711_16K;
        main[1] = 0;
        main[2] = (byte) (PmrSocket.Priznak_pmr & 0xFF);
        main[3] = (byte) ((PmrSocket.Priznak_pmr >> 8) & 0xFF);
        System.arraycopy(g711buf, 0, main, 4, payloadLen);

        pmrSocket.sendVoice(main, reserve);
    }

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
        int scaled = (int) (rms / 32.0);
        if (scaled > 1000) scaled = 1000;
        return scaled;
    }
}