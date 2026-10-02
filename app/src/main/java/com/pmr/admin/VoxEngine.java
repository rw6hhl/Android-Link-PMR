package com.pmr.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/* VOX-движок Android Link PMR V1.7.
 *
 * Изменения V1.7:
 *   - добавлено усиление микрофона vox_mic (0..100);
 *   - vox_mic = 0 → усиление отключено (1.0x);
 *   - vox_mic = 100 → MicUsildouble = 2.0x;
 *   - усиление применяется ДО расчёта RMS — чтобы порог срабатывал
 *     на усиленном сигнале.
 */
public class VoxEngine {

    private static final int SAMPLE_RATE = 16000;
    private static final int BUF_ELEMENTS = 320;
    private static final int BYTES_PER_ELEM = 2;
    private static final int BUF_SIZE = BUF_ELEMENTS * BYTES_PER_ELEM;

    /* Шкала RMS: 100 = тихий, 500 = средний, 800 = громкий. */
    private static final double RMS_DIVISOR = 25.0;

    /* Флаги усиления. */
    private static final int onUsilMic = 1;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioRecord recorder = null;
    private Thread voxThread = null;
    private volatile boolean running = false;

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

            /* Усиление микрофона — до расчёта RMS. */
            byte[] amplified = applyMicGain(pcm16, read, voxMic);

            int rms = calcRms(amplified, read);
            lastRms = rms;

            diagCounter++;
            if (diagCounter >= 50) {
                AppLog.add("VoxEngine: rms=" + rms + ", vox=" + myVox
                        + ", mic=" + voxMic + ", tx=" + txActive);
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
                sendFrame(amplified, read, g711buf);
            } else {
                if (txActive) {
                    silentTicks++;
                    if (silentTicks >= voxPause) {
                        txActive = false;
                        AppLog.add("VoxEngine: TX OFF (silent="
                                + silentTicks + ", pause=" + voxPause + ")");
                        silentTicks = 0;
                    } else {
                        sendFrame(amplified, read, g711buf);
                    }
                }
            }
        }
    }

    /* Усиление микрофона: 0..100 → 1.0..2.0. vox_mic=0 → 1.0. */
    private byte[] applyMicGain(byte[] pcm, int len, int voxMic) {
        if (onUsilMic == 0 || voxMic <= 0) {
            return pcm;
        }
        double micUsil = (double) voxMic / 50.0;
        if (micUsil <= 1.0) {
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

    /* Масштаб 0..1000, делитель 25.0. */
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