package com.pmr.admin;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/* Звуковой движок Android Link PMR V1.9.
 *
 * Изменения V1.9:
 *   - добавлено поле lastRxTime и метод markRxActivity();
 *   - добавлен метод isRxActive() — true, если приём был < 500 мс назад;
 *   - DinUsildouble применяется к lastRxRms при onUsilDin = 1;
 *   - лог getMinBufferSize() при startPlaying().
 */
public class AudioEngine {

    public static final int CMD_PCM8_16K  = 19;
    public static final int CMD_PCM16_16K = 21;
    public static final int CMD_G711_16K  = 22;
    public static final int CMD_PCM16_8K  = 25;
    public static final int CMD_G711_8K   = 26;
    public static final int CMD_PCM8_8K   = 27;

    private static final int SAMPLE_RATE_16K = 16000;
    private static final int SAMPLE_RATE_8K  = 8000;
    private static final int SLOTS_PER_FORMAT = 20;
    private static final int OFFSET_8K = 20;

    private static final float VOLUME_BOOST = 1.7f;
    private static final double RMS_DIVISOR = 25.0;

    /* Таймаут приёма: если новых пакетов нет дольше — считаем, что приём завершён. */
    private static final long RX_TIMEOUT_MS = 500L;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioTrack[] tracks = new AudioTrack[40];
    private volatile boolean isPlaying = false;
    private volatile int lastRxRms = 0;

    /* Время последнего принятого пакета — для isRxActive(). */
    private volatile long lastRxTime = 0L;

    public AudioEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isPlaying() { return isPlaying; }
    public int getLastRxRms()  { return lastRxRms; }

    /* Новый метод: пометить, что только что пришёл звук от сервера. */
    public void markRxActivity() {
        lastRxTime = System.currentTimeMillis();
    }

    /* Новый метод: идёт ли приём прямо сейчас. */
    public boolean isRxActive() {
        long t = lastRxTime;
        if (t == 0L) return false;
        return (System.currentTimeMillis() - t) < RX_TIMEOUT_MS;
    }

    public void startPlaying() {
        if (isPlaying) return;

        logCurrentAudioDevice("AudioEngine");
        AppLog.add("AudioEngine: использование " +
                (isUsbPresent() ? "USB-аудио" : "встроенного динамика"));

        int minSize16 = AudioTrack.getMinBufferSize(SAMPLE_RATE_16K,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int minSize8 = AudioTrack.getMinBufferSize(SAMPLE_RATE_8K,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        AppLog.add("AudioEngine: minBufferSize16=" + minSize16
                + " (" + (minSize16 / 32) + " мс), minBufferSize8="
                + minSize8 + " (" + (minSize8 / 16) + " мс)");

        for (int i = 0; i < SLOTS_PER_FORMAT; i++) {
            if (tracks[i] == null) {
                tracks[i] = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE_16K,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minSize16,
                        AudioTrack.MODE_STREAM);
                try { tracks[i].setVolume(VOLUME_BOOST); } catch (Exception ignored) {}
            }
        }
        for (int i = OFFSET_8K; i < OFFSET_8K + SLOTS_PER_FORMAT; i++) {
            if (tracks[i] == null) {
                tracks[i] = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE_8K,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minSize8,
                        AudioTrack.MODE_STREAM);
                try { tracks[i].setVolume(VOLUME_BOOST); } catch (Exception ignored) {}
            }
        }
        isPlaying = true;
        AppLog.add("AudioEngine: startPlaying, volume=" + VOLUME_BOOST
                + ", slots=" + tracks.length);
    }

    public void stopPlaying() {
        isPlaying = false;
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] != null) {
                try {
                    tracks[i].flush();
                    tracks[i].stop();
                    tracks[i].release();
                } catch (Exception ignored) {}
                tracks[i] = null;
            }
        }
    }

    private boolean isUsbPresent() {
        AudioManager am = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
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
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo d : devs) {
            CharSequence pn = d.getProductName();
            String name = (pn != null) ? pn.toString() : "?";
            AppLog.add(tag + ": output device type=" + d.getType()
                    + ", name=" + name);
        }
    }

    private boolean isValid16(int client) {
        return client >= 0 && client < SLOTS_PER_FORMAT;
    }

    private boolean isValid8(int client) {
        return client >= 0 && client < SLOTS_PER_FORMAT;
    }

    /* RX-уровень: RMS × DinUsildouble (если включено), делитель 25.0. */
    private void updateRxRms(byte[] pcm, int len) {
        long sum = 0;
        int n = len / 2;
        for (int i = 0; i < n; i++) {
            short s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            sum += (long) s * s;
        }
        if (n == 0) { lastRxRms = 0; return; }
        double mean = (double) sum / n;
        double rms = Math.sqrt(mean);

        /* Читаем флаг onUsilDin и коэффициент DinUsildouble из SharedPreferences. */
        double usil = 1.0;
        try {
            android.content.SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            int onUsilDin = sp.getInt(PasswordActivity.KEY_USIL_DIN,
                    PasswordActivity.DEFAULT_USIL_DIN);
            if (onUsilDin != 0) {
                String dinStr = sp.getString(PasswordActivity.KEY_DIN_USIL,
                        String.valueOf(PasswordActivity.DEFAULT_DIN_USIL));
                try {
                    usil = Double.parseDouble(dinStr);
                } catch (NumberFormatException ignored) {
                    usil = PasswordActivity.DEFAULT_DIN_USIL;
                }
            }
        } catch (Exception ignored) {}

        int scaled = (int) ((rms * usil) / RMS_DIVISOR);
        if (scaled > 1000) scaled = 1000;
        if (scaled < 0)    scaled = 0;
        lastRxRms = scaled;
    }

    public void playG711_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        if (tracks[client] == null) return;
        byte[] pcm = new byte[640];
        g711.decode(buf, 4, 320, pcm);
        updateRxRms(pcm, 640);
        try {
            tracks[client].write(pcm, 0, 640);
            if (tracks[client].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[client].play();
            }
        } catch (Exception ignored) {}
    }

    public void playG711_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        if (tracks[slot] == null) return;
        byte[] pcm = new byte[320];
        g711.decode(buf, 4, 160, pcm);
        updateRxRms(pcm, 320);
        try {
            tracks[slot].write(pcm, 0, 320);
            if (tracks[slot].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[slot].play();
            }
        } catch (Exception ignored) {}
    }

    public void playPCM16_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        if (tracks[client] == null) return;
        updateRxRms(buf, 640);
        try {
            tracks[client].write(buf, 4, 640);
            if (tracks[client].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[client].play();
            }
        } catch (Exception ignored) {}
    }

    public void playPCM16_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        if (tracks[slot] == null) return;
        updateRxRms(buf, 320);
        try {
            tracks[slot].write(buf, 4, 320);
            if (tracks[slot].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[slot].play();
            }
        } catch (Exception ignored) {}
    }

    public void playPCM8_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        if (tracks[client] == null) return;
        short[] pcm = new short[320];
        for (int i = 4; i < 324; i++) {
            pcm[i - 4] = (short) (buf[i] * 256);
        }
        byte[] out = short2byte(pcm);
        updateRxRms(out, 640);
        try {
            tracks[client].write(out, 0, 640);
            if (tracks[client].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[client].play();
            }
        } catch (Exception ignored) {}
    }

    public void playPCM8_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        if (tracks[slot] == null) return;
        short[] pcm = new short[160];
        for (int i = 4; i < 164; i++) {
            pcm[i - 4] = (short) (buf[i] * 256);
        }
        byte[] out = short2byte(pcm);
        updateRxRms(out, 320);
        try {
            tracks[slot].write(out, 0, 320);
            if (tracks[slot].getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                tracks[slot].play();
            }
        } catch (Exception ignored) {}
    }

    private static byte[] short2byte(short[] sArr) {
        int length = sArr.length;
        byte[] bArr = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            bArr[i2] = (byte) (sArr[i] & 255);
            bArr[i2 + 1] = (byte) (sArr[i] >> 8);
        }
        return bArr;
    }
}