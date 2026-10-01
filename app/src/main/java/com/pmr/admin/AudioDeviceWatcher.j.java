package com.pmr.admin;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/* Слежение за сменой USB-аудио устройства V1.2.
 *
 * При подключении / отключении USB-аудио вызывается onDeviceChanged,
 * чтобы AudioEngine и VoxEngine пересоздали AudioTrack/AudioRecord
 * с новой маршрутизацией.
 */
public class AudioDeviceWatcher {

    public interface Callback {
        void onDeviceChanged(boolean usbConnected, String usbName);
    }

    private final Context appCtx;
    private final AudioManager am;
    private final Callback cb;
    private final AudioDeviceCallback adc;

    private boolean usbConnected = false;
    private String usbName = "";

    public AudioDeviceWatcher(Context ctx, Callback callback) {
        this.appCtx = ctx;
        this.cb = callback;
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        this.adc = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                scan();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                scan();
            }
        };
    }

    public boolean isUsbConnected() { return usbConnected; }
    public String getUsbName()      { return usbName; }

    public void start() {
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.registerAudioDeviceCallback(adc, new Handler());
            } catch (Exception e) {
                AppLog.add("AudioDeviceWatcher: register FAIL — " + e);
            }
        }
        scan();
    }

    public void stop() {
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.unregisterAudioDeviceCallback(adc);
            } catch (Exception ignored) {}
        }
    }

    /* Сканирование устройств, обновление статуса и вызов cb. */
    public void scan() {
        if (am == null) return;
        boolean found = false;
        String name = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_ALL);
            for (AudioDeviceInfo d : devs) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_USB_DEVICE
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET
                        || t == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                    found = true;
                    CharSequence pn = d.getProductName();
                    name = (pn != null) ? pn.toString() : "USB-аудио";
                    break;
                }
            }
        }
        boolean changed = (found != usbConnected)
                || (!name.equals(usbName));
        usbConnected = found;
        usbName = name;
        if (cb != null && changed) {
            AppLog.add("AudioDeviceWatcher: usb=" + usbConnected
                    + ", name=" + usbName);
            cb.onDeviceChanged(usbConnected, usbName);
        }
    }
}