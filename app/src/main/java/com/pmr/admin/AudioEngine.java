package com.pmr.admin;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/* Звуковой движок Android Link PMR V1.4.
 *
 * Изменения V1.4:
 *   - при старте определяется текущее аудиоустройство и пишется в лог;
 *   - AudioTrack создаются с учётом активного устройства;
 *   - смена устройства требует перезапуска приложения.
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

    private final Context appCtx;
    private final PmrSocket pmrSocket;
    private final G711Ua g711 = new G711Ua();

    private AudioTrack[] tracks = new AudioTrack[40];
    private volatile boolean isPlaying = false;

    public AudioEngine(Context ctx, PmrSocket sock) {
        this.appCtx = ctx;
        this.pmrSocket = sock;
    }

    public boolean isPlaying() { return isPlaying; }

    public void startPlaying() {
        if (isPlaying) return;

        logCurrentAudioDevice("AudioEngine");
        AppLog.add("AudioEngine: использование " +
                (isUsbPresent() ? "USB-аудио" : "встроенного динамика"));

        for (int i = 0; i < SLOTS_PER_FORMAT; i++) {
            if (tracks[i] == null) {
                tracks[i] = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE_16K,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioTrack.getMinBufferSize(SAMPLE_RATE_16K,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT),
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
                        AudioTrack.getMinBufferSize(SAMPLE_RATE_8K,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT),
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

    public void playG711_16k(int client, byte[] buf, int len) {
        if (!isPlaying || !isValid16(client)) return;
        if (tracks[client] == null) return;
        byte[] pcm = new byte[640];
        g711.decode(buf, 4, 320, pcm);
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