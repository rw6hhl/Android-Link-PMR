package com.pmr.admin;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.HashMap;

/* Управление PTT через GPIO3 CM108 (Android Link PMR V2.5.2).
 *
 * Изменения V2.5.2:
 *   - перед отправкой PTT проверяется, что AudioTrack/активное
 *     устройство вывода — это USB-аудио (CM108). Если да —
 *     PTT отправляется. Если нет — PTT не отправляется.
 *     Это защищает от ложного включения PTT на встроенном динамике.
 *
 * Формат команды GPIO3: [0, 0, 4, state ? 4 : 0, 0]
 */
public class Cm108PttController {

    /* VID:PID CM108 (Digirig Lite). */
    private static final int CM108_VID = 0x0D8C;
    private static final int CM108_PID = 0x0012;

    /* GPIO3: бит 2. */
    private static final int GPIO3_MASK = 1 << 2;   // 4

    private static final int USB_TYPE_CLASS = 0x20;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_DIR_OUT = 0x00;
    private static final int HID_SET_REPORT = 0x09;
    private static final int HID_REPORT_TYPE_OUTPUT = 0x0200;

    private final Context appCtx;

    private UsbDeviceConnection connection = null;
    private UsbInterface hidInterface = null;
    private boolean ready = false;

    /* Текущее состояние PTT. */
    private volatile boolean pttActive = false;

    public Cm108PttController(Context ctx) {
        this.appCtx = ctx;
    }

    public boolean isPttActive() { return pttActive; }
    public boolean isReady()      { return ready; }

    public void init() {
        try {
            UsbManager usbManager = (UsbManager) appCtx.getSystemService(
                    Context.USB_SERVICE);
            if (usbManager == null) {
                AppLog.add("Cm108Ptt: UsbManager == null");
                return;
            }
            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            UsbDevice target = null;
            for (UsbDevice d : devices.values()) {
                if (d.getVendorId() == CM108_VID && d.getProductId() == CM108_PID) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                AppLog.add("Cm108Ptt: CM108 не найден (VID=0x0D8C, PID=0x0012)");
                return;
            }
            if (!usbManager.hasPermission(target)) {
                AppLog.add("Cm108Ptt: нет разрешения на USB-устройство");
                return;
            }

            for (int i = 0; i < target.getInterfaceCount(); i++) {
                UsbInterface ifc = target.getInterface(i);
                if (ifc.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                    hidInterface = ifc;
                    break;
                }
            }
            if (hidInterface == null) {
                AppLog.add("Cm108Ptt: HID-интерфейс не найден");
                return;
            }

            connection = usbManager.openDevice(target);
            if (connection == null) {
                AppLog.add("Cm108Ptt: openDevice == null");
                return;
            }
            if (!connection.claimInterface(hidInterface, true)) {
                AppLog.add("Cm108Ptt: claimInterface FAIL");
                connection.close();
                connection = null;
                return;
            }
            ready = true;
            AppLog.add("Cm108Ptt: готов, HID interface="
                    + hidInterface.getId());
        } catch (Exception e) {
            AppLog.add("Cm108Ptt: init FAIL — " + e);
            ready = false;
        }
    }

    /* Установить PTT (GPIO3). */
    public void setPtt(boolean on) {
        if (!ready || connection == null || hidInterface == null) {
            pttActive = on;
            return;
        }

        /* Проверка: активное устройство вывода — USB? */
        if (on && !isUsbOutputActive()) {
            AppLog.add("Cm108Ptt: PTT ON отклонён — активный output не USB");
            return;
        }

        if (pttActive == on) return;

        try {
            byte[] data = new byte[5];
            data[0] = 0;
            data[1] = 0;
            data[2] = (byte) GPIO3_MASK;
            data[3] = (byte) (on ? GPIO3_MASK : 0);
            data[4] = 0;

            int requestType = USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE;
            int result = connection.controlTransfer(
                    requestType,
                    HID_SET_REPORT,
                    HID_REPORT_TYPE_OUTPUT,
                    hidInterface.getId(),
                    data,
                    data.length,
                    250);

            if (result >= 0) {
                pttActive = on;
                AppLog.add("Cm108Ptt: PTT " + (on ? "ON" : "OFF")
                        + " (result=" + result + ")");
            } else {
                AppLog.add("Cm108Ptt: controlTransfer FAIL result=" + result);
            }
        } catch (Exception e) {
            AppLog.add("Cm108Ptt: setPtt FAIL — " + e);
        }
    }

    /* Проверка: активный output — USB-аудио? */
    private boolean isUsbOutputActive() {
        AudioManager am = (AudioManager) appCtx.getSystemService(
                Context.AUDIO_SERVICE);
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

    public void release() {
        try {
            if (connection != null) {
                if (hidInterface != null) {
                    connection.releaseInterface(hidInterface);
                }
                connection.close();
            }
        } catch (Exception ignored) {}
        connection = null;
        hidInterface = null;
        ready = false;
        pttActive = false;
    }
}