package com.pmr.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/* Звуковой движок Android Link PMR V4.0-BETA.
 *
 * Изменения V4.0-BETA:
 *   - ЛЕНИВОЕ создание AudioTrack — на Android 8.1 создание 40 AudioTrack
 *     сразу приводило к ANR (главный поток блокировался на 5-10 сек);
 *   - AudioTrack создаётся при первом play*() для конкретного клиента;
 *   - частота тона PTT — из KEY_PTT_TONE_HZ (100..3000 Гц);
 *   - ptt_tone_level = 0 → тон выключен;
 *   - сохранена СТЕРЕО-логика: R = звук, L = тон 1000 Гц.
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
    private static final long RX_TIMEOUT_MS = 500L;

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private final AudioTrack[] tracks = new AudioTrack[40];
    private volatile boolean isPlaying = false;
    private volatile int lastRxRms = 0;
    private volatile long lastRxTime = 0L;

    /* Фаза генератора тона — общая для всех. */
    private double tonePhase = 0.0;

    public AudioEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isPlaying() { return isPlaying; }
    public int getLastRxRms()  { return lastRxRms; }
    public boolean isRxActive() {
        long t = lastRxTime;
        if (t == 0L) return false;
        return (System.currentTimeMillis() - t) < RX_TIMEOUT_MS;
    }

    public void markRxActivity() {
        lastRxTime = System.currentTimeMillis();
    }

    public void checkRxTimeout() {
        /* Ничего — тон гаснет автоматически, когда isRxActive() = false. */
    }

    /* Быстрая инициализация — без создания AudioTrack. */
    public void startPlaying() {
        if (isPlaying) return;
        isPlaying = true;
        AppLog.add("AudioEngine V4.0-BETA: старт, ленивая инициализация");
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

    /* Ленивое создание AudioTrack. */
    private AudioTrack ensureTrack(int slot, int sampleRate) {
        if (slot < 0 || slot >= tracks.length) return null;
        if (tracks[slot] != null) return tracks[slot];
        try {
            int minSize = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack t = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minSize * 2,
                    AudioTrack.MODE_STREAM);
            try { t.setVolume(VOLUME_BOOST); } catch (Exception ignored) {}
            tracks[slot] = t;
            AppLog.add("AudioEngine: создан AudioTrack slot=" + slot
                    + ", rate=" + sampleRate);
            return t;
        } catch (Exception e) {
            AppLog.add("AudioEngine: ошибка создания AudioTrack — " + e);
            return null;
        }
    }

    private boolean isValid16(int client) {
        return client >= 0 && client < SLOTS_PER_FORMAT;
    }

    private boolean isValid8(int client) {
        return client >= 0 && client < SLOTS_PER_FORMAT;
    }

    /* Уровень тона PTT (0..100 %). 0 = тон выключен. */
    private int getPttToneLevel() {
        try {
            SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            return sp.getInt(PasswordActivity.KEY_PTT_TONE_LEVEL,
                    PasswordActivity.DEFAULT_PTT_TONE_LEVEL);
        } catch (Exception e) {
            return PasswordActivity.DEFAULT_PTT_TONE_LEVEL;
        }
    }

    /* Частота тона PTT (100..3000 Гц). */
    private int getPttToneHz() {
        try {
            SharedPreferences sp = appCtx.getSharedPreferences(
                    PasswordActivity.PREFS, Context.MODE_PRIVATE);
            int hz = sp.getInt(PasswordActivity.KEY_PTT_TONE_HZ,
                    PasswordActivity.DEFAULT_PTT_TONE_HZ);
            if (hz < 100) hz = 100;
            if (hz > 3000) hz = 3000;
            return hz;
        } catch (Exception e) {
            return PasswordActivity.DEFAULT_PTT_TONE_HZ;
        }
    }

    /* Формирование стерео-буфера: R = звук, L = тон PTT. */
    private byte[] buildStereoBuffer(byte[] pcmMono) {
        int n = pcmMono.length / 2;
        byte[] out = new byte[n * 4];
        boolean rxActive = isRxActive();
        int toneLevel = getPttToneLevel();
        int toneHz = getPttToneHz();

        if (toneLevel < 0) toneLevel = 0;
        if (toneLevel > 100) toneLevel = 100;
        int toneAmp = (int) (32767.0 * toneLevel / 100.0);

        double dPhase = 2.0 * Math.PI * toneHz / SAMPLE_RATE_16K;

        for (int i = 0; i < n; i++) {
            short left;
            if (rxActive && toneLevel > 0) {
                left = (short) (toneAmp * Math.sin(tonePhase));
                tonePhase += dPhase;
                if (tonePhase >= 2.0 * Math.PI) tonePhase -= 2.0 * Math.PI;
            } else {
                left = 0;
                tonePhase = 0.0;
            }

            short right = (short) ((pcmMono[i * 2] & 0xFF)
                    | (pcmMono[i * 2 + 1] << 8));

            int idx = i * 4;
            out[idx]     = (byte) (left & 0xFF);
            out[idx + 1] = (byte) ((left >> 8) & 0xFF);
            out[idx + 2] = (byte) (right & 0xFF);
            out[idx + 3] = (byte) ((right >> 8) & 0xFF);
        }
        return out;
    }

    /* Воспроизведение — с ленивым созданием AudioTrack. */
    private void playPcm(int slot, int sampleRate, byte[] pcmMono) {
        AudioTrack t = ensureTrack(slot, sampleRate);
        if (t == null) return;
        byte[] stereo = buildStereoBuffer(pcmMono);
        try {
            t.write(stereo, 0, stereo.length);
            if (t.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                t.play();
            }
        } catch (Exception ignored) {}
    }

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

        double usil = 1.0;
        try {
            SharedPreferences sp = appCtx.getSharedPreferences(
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
        byte[] pcm = new byte[640];
        g711.decode(buf, 4, 320, pcm);
        updateRxRms(pcm, 640);
        playPcm(client, SAMPLE_RATE_16K, pcm);
    }

    public void playG711_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        byte[] pcm = new byte[320];
        g711.decode(buf, 4, 160, pcm);
        updateRxRms(pcm, 320);
        playPcm(slot, SAMPLE_RATE_8K, pcm);
    }

    public void playPCM16_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        byte[] pcm = new byte[640];
        System.arraycopy(buf, 4, pcm, 0, 640);
        updateRxRms(pcm, 640);
        playPcm(client, SAMPLE_RATE_16K, pcm);
    }

    public void playPCM16_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        byte[] pcm = new byte[320];
        System.arraycopy(buf, 4, pcm, 0, 320);
        updateRxRms(pcm, 320);
        playPcm(slot, SAMPLE_RATE_8K, pcm);
    }

    public void playPCM8_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        short[] pcm = new short[320];
        for (int i = 4; i < 324; i++) {
            pcm[i - 4] = (short) (buf[i] * 256);
        }
        byte[] out = short2byte(pcm);
        updateRxRms(out, 640);
        playPcm(client, SAMPLE_RATE_16K, out);
    }

    public void playPCM8_8k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid8(client)) return;
        int slot = client + OFFSET_8K;
        short[] pcm = new short[160];
        for (int i = 4; i < 164; i++) {
            pcm[i - 4] = (short) (buf[i] * 256);
        }
        byte[] out = short2byte(pcm);
        updateRxRms(out, 320);
        playPcm(slot, SAMPLE_RATE_8K, out);
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