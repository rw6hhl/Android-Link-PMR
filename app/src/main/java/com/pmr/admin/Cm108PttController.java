package com.pmr.admin;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.HashMap;

/* Управление PTT через GPIO3 CM108 (Android Link PMR V2.5).
 *
 * Работает через Android USB Host API:
 *   - находит CM108 по VID:PID 0D8C:0012 (Digirig Lite);
 *   - ищет HID-интерфейс (класс USB_CLASS_HID);
 *   - открывает соединение и заявляет интерфейс;
 *   - отправляет HID Set_Output_Report через controlTransfer().
 *
 * Формат команды GPIO3: [0, 0, 4, state ? 4 : 0, 0]
 *   байт 0: report number = 0
 *   байт 1: зарезервировано = 0
 *   байт 2: iomask (data direction) = 4 (1 << 2)
 *   байт 3: iodata = 4 (вкл) или 0 (выкл)
 *   байт 4: зарезервировано = 0
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

    public Cm108PttController(Context ctx) {
        this.appCtx = ctx;
    }

    public boolean isPttActive() { return pttActive; }
    public boolean isReady()      { return ready; }

    /* Инициализация: поиск CM108, открытие, заявление интерфейса. */
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

            /* Ищем HID-интерфейс. */
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
            /* CM108 недоступен — просто запоминаем состояние. */
            pttActive = on;
            return;
        }
        if (pttActive == on) return;

        try {
            byte[] data = new byte[5];
            data[0] = 0;                        /* report number */
            data[1] = 0;                        /* reserved */
            data[2] = (byte) GPIO3_MASK;        /* iomask */
            data[3] = (byte) (on ? GPIO3_MASK : 0); /* iodata */
            data[4] = 0;                        /* reserved */

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

    /* Освободить ресурсы. */
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