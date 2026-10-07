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

/* Управление PTT через GPIO3 CM108 (Android Link PMR V2.6).
 *
 * Изменения V2.6:
 *   - убран claimInterface — он блокировал USB-устройство;
 *   - убран releaseInterface;
 *   - добавлен retryInitIfNeeded() — периодическая попытка init()
 *     если разрешение появилось после старта службы;
 *   - возвращена проверка isUsbOutputActive() — PTT включается только
 *     если активный вывод — USB-аудио (CM108). Защищает от ложного
 *     включения PTT при выводе на встроенный динамик;
 *   - подробная диагностика на каждом шаге.
 *
 * Формат команды GPIO3: [0, 0, 4, state ? 4 : 0, 0]
 */
public class Cm108PttController {

    /* VID:PID CM108 (Digirig Lite). */
    private static final int CM108_VID = 0x0D8C;
    private static final int CM108_PID = 0x0012;

    /* GPIO3: бит 2. */
    private static final int GPIO3_MASK = 1 << 2;   // 4

    /* HID-константы. */
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

    /* Защита от повторных попыток init(). */
    private long lastRetryTime = 0L;
    private static final long RETRY_INTERVAL_MS = 2000L;

    public Cm108PttController(Context ctx) {
        this.appCtx = ctx;
    }

    public boolean isPttActive() { return pttActive; }
    public boolean isReady()      { return ready; }

    /* Инициализация: поиск CM108, открытие, поиск HID-интерфейса. */
    public void init() {
        AppLog.add("Cm108Ptt: init() — начало");
        try {
            UsbManager usbManager = (UsbManager) appCtx.getSystemService(
                    Context.USB_SERVICE);
            if (usbManager == null) {
                AppLog.add("Cm108Ptt: UsbManager == null");
                return;
            }
            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            AppLog.add("Cm108Ptt: устройств USB найдено " + devices.size());
            UsbDevice target = null;
            for (UsbDevice d : devices.values()) {
                AppLog.add("Cm108Ptt: USB device VID=0x"
                        + Integer.toHexString(d.getVendorId())
                        + ", PID=0x"
                        + Integer.toHexString(d.getProductId())
                        + ", name=" + d.getProductName());
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
            AppLog.add("Cm108Ptt: разрешение есть, ищем HID-интерфейс");

            for (int i = 0; i < target.getInterfaceCount(); i++) {
                UsbInterface ifc = target.getInterface(i);
                AppLog.add("Cm108Ptt: интерфейс " + i + ", класс="
                        + ifc.getInterfaceClass()
                        + ", subclass=" + ifc.getInterfaceSubclass()
                        + ", endpoints=" + ifc.getEndpointCount());
                if (ifc.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
                    hidInterface = ifc;
                    AppLog.add("Cm108Ptt: HID-интерфейс найден, id="
                            + ifc.getId());
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
            AppLog.add("Cm108Ptt: openDevice OK");

            /* claimInterface УБРАН — он блокировал USB-устройство.
             * controlTransfer работает без заявки интерфейса. */

            ready = true;
            AppLog.add("Cm108Ptt: готов (без claimInterface)");
        } catch (Exception e) {
            AppLog.add("Cm108Ptt: init FAIL — " + e);
            ready = false;
        }
    }

    /* Периодическая попытка init(), если разрешение появилось позже. */
    public void retryInitIfNeeded() {
        if (ready) return;
        long now = System.currentTimeMillis();
        if (now - lastRetryTime < RETRY_INTERVAL_MS) return;
        lastRetryTime = now;
        AppLog.add("Cm108Ptt: retryInitIfNeeded() — попытка init()");
        init();
    }

    /* Установить PTT (GPIO3).
     * Если on == true — проверяем, что активный вывод — USB.
     * Если нет — PTT не включаем (защита от ложного PTT на динамике). */
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
            data[0] = 0;                            /* report number */
            data[1] = 0;                            /* reserved */
            data[2] = (byte) GPIO3_MASK;            /* iomask */
            data[3] = (byte) (on ? GPIO3_MASK : 0); /* iodata */
            data[4] = 0;                            /* reserved */

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

    /* Освободить ресурсы. */
    public void release() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {}
        connection = null;
        hidInterface = null;
        ready = false;
        pttActive = false;
    }
}