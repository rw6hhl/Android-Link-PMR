package com.pmr.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/* VOX-движок Android Link PMR V2.0.
 *
 * Изменения V2.0:
 *   - добавлено скользящее окно максимума RMS за 3 секунды;
 *   - метод getMaxRms3Sec() возвращает максимум за последние 3 сек;
 *   - окно — 150 значений (по 20 мс каждое = 3 секунды).
 */
public class VoxEngine {

    private static final int SAMPLE_RATE = 16000;
    private static final int BUF_ELEMENTS = 320;
    private static final int BYTES_PER_ELEM = 2;
    private static final int BUF_SIZE = BUF_ELEMENTS * BYTES_PER_ELEM;

    private static final double RMS_DIVISOR = 25.0;
    private static final int VOX_HYSTERESIS = 3;

    /* Скользящее окно: 150 значений по 20 мс = 3000 мс = 3 секунды. */
    private static final int MAX_WINDOW_SIZE = 150;
    private final int[] maxWindow = new int[MAX_WINDOW_SIZE];
    private int maxWindowIndex = 0;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioRecord recorder = null;
    private Thread voxThread = null;
    private volatile boolean running = false;

    private volatile boolean txActive = false;
    private volatile int lastRms = 0;

    /* Максимум за окно — volatile для чтения из UI. */
    private volatile int maxRms3Sec = 0;

    private int ticPoslePrd = 0;

    public VoxEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isTxActive() { return txActive; }
    public int getLastRms()     { return lastRms; }
    public int getMaxRms3Sec()  { return maxRms3Sec; }

    public void start() {
        if (running) return;

        logCurrentAudioDevice("VoxEngine");
        AppLog.add("VoxEngine: использование " +
                (isUsbPresent() ? "USB-аудио" : "встроенного микрофона"));

        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUF_SIZE * 2);
            recorder.startRecording();
            AppLog.add("VoxEngine: AudioRecord init OK");
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

    private boolean isUsbPresent() {
        AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo d : devs) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_USB_DEVICE
                    || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || t == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                return true;
            }
        }
        return false;
    }

    private void logCurrentAudioDevice(String tag) {
        AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo d : devs) {
            CharSequence pn = d.getProductName();
            String name = (pn != null) ? pn.toString() : "?";
            AppLog.add(tag + ": input device type=" + d.getType()
                    + ", name=" + name);
        }
    }

    /* Обновить скользящее окно максимума. Вызывается в loop(). */
    private void updateMaxWindow(int rms) {
        maxWindow[maxWindowIndex] = rms;
        maxWindowIndex = (maxWindowIndex + 1) % MAX_WINDOW_SIZE;
        int m = 0;
        for (int i = 0; i < MAX_WINDOW_SIZE; i++) {
            if (maxWindow[i] > m) m = maxWindow[i];
        }
        maxRms3Sec = m;
    }

    private void loop() {
        byte[] pcm16 = new byte[BUF_SIZE];
        byte[] g711buf = new byte[BUF_ELEMENTS];
        int silentTicks = 0;
        int diagCounter = 0;

        while (running) {
            int read;
            try {
                read = recorder.read(pcm16, 0, pcm16.length);
            } catch (Exception e) {
                break;
            }
            if (read <= 0) continue;

            SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            int myVox = sp.getInt(PasswordActivity.KEY_MY_VOX,
                    PasswordActivity.DEFAULT_MY_VOX);
            int voxPause = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                    PasswordActivity.DEFAULT_VOX_PAUSE);
            int voxMic = sp.getInt(PasswordActivity.KEY_VOX_MIC,
                    PasswordActivity.DEFAULT_VOX_MIC);
            int poslePrd = sp.getInt(PasswordActivity.KEY_POSLE_PRD,
                    PasswordActivity.DEFAULT_POSLE_PRD);
            int onUsilMic = sp.getInt(PasswordActivity.KEY_USIL_MIC,
                    PasswordActivity.DEFAULT_USIL_MIC);

            double micUsil = 1.0;
            if (onUsilMic != 0) {
                String micStr = sp.getString(PasswordActivity.KEY_MIC_USIL,
                        String.valueOf(PasswordActivity.DEFAULT_MIC_USIL));
                try {
                    micUsil = Double.parseDouble(micStr);
                } catch (NumberFormatException ignored) {
                    micUsil = PasswordActivity.DEFAULT_MIC_USIL;
                }
            }

            byte[] amplified = applyMicGain(pcm16, read, micUsil);
            int rms = calcRms(amplified, read);
            lastRms = rms;

            /* Обновить скользящее окно максимума за 3 секунды. */
            updateMaxWindow(rms);

            diagCounter++;
            if (diagCounter >= 50) {
                AppLog.add("VoxEngine: rms=" + rms + ", vox=" + myVox
                        + ", mic=" + voxMic + ", usil=" + micUsil
                        + ", tx=" + txActive
                        + ", prd=" + ticPoslePrd
                        + ", max3s=" + maxRms3Sec
                        + ", rx=" + (PmrService.audioEngine != null
                                && PmrService.audioEngine.isRxActive()));
                diagCounter = 0;
            }

            if (PmrService.audioEngine != null
                    && PmrService.audioEngine.isRxActive()) {
                if (txActive) {
                    txActive = false;
                    AppLog.add("VoxEngine: TX OFF (RX active)");
                }
                silentTicks = 0;
                ticPoslePrd = poslePrd;
                continue;
            }

            if (ticPoslePrd > 0) {
                ticPoslePrd--;
                if (txActive) {
                    txActive = false;
                    AppLog.add("VoxEngine: TX OFF (prd)");
                }
                silentTicks = 0;
                continue;
            }

            if (myVox <= 0) {
                if (txActive) {
                    txActive = false;
                    AppLog.add("VoxEngine: VOX off (myVox=0)");
                }
                silentTicks = 0;
                continue;
            }

            if (rms >= myVox + VOX_HYSTERESIS) {
                silentTicks = 0;
                if (!txActive) {
                    txActive = true;
                    AppLog.add("VoxEngine: TX ON (rms=" + rms
                            + ", vox=" + myVox + ")");
                }
                sendFrame(amplified, read, g711buf);
            } else {
                if (txActive) {
                    silentTicks++;
                    if (silentTicks >= voxPause) {
                        txActive = false;
                        ticPoslePrd = poslePrd;
                        AppLog.add("VoxEngine: TX OFF (silent="
                                + silentTicks + ", pause=" + voxPause
                                + ", prd=" + ticPoslePrd + ")");
                        silentTicks = 0;
                    } else {
                        sendFrame(amplified, read, g711buf);
                    }
                }
            }
        }
    }

    private byte[] applyMicGain(byte[] pcm, int len, double micUsil) {
        if (micUsil <= 1.0 || micUsil <= 0.0) {
            return pcm;
        }
        byte[] out = new byte[len];
        for (int i = 0; i < len; i += 2) {
            short s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int amplified = (int) (s * micUsil);
            if (amplified > 32767) amplified = 32767;
            if (amplified < -32768) amplified = -32768;
            out[i] = (byte) (amplified & 0xFF);
            out[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }
        return out;
    }

    private void sendFrame(byte[] pcm16, int read, byte[] g711buf) {
        if (pmrSocket == null) return;

        g711.encode(pcm16, 0, read, g711buf);
        int payloadLen = read / 2;

        byte[] main = new byte[4 + payloadLen];
        main[0] = (byte) AudioEngine.CMD_G711_16K;
        main[1] = 0;
        main[2] = (byte) (PmrSocket.Priznak_pmr & 0xFF);
        main[3] = (byte) ((PmrSocket.Priznak_pmr >> 8) & 0xFF);
        System.arraycopy(g711buf, 0, main, 4, payloadLen);

        pmrSocket.sendVoice(main, null);
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
        int scaled = (int) (rms / RMS_DIVISOR);
        if (scaled > 1000) scaled = 1000;
        return scaled;
    }
}