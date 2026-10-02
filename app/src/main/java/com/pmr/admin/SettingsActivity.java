package com.pmr.admin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/* Экран настроек Android Link PMR V1.6.
 *
 * Изменения V1.6:
 *   - vox_pause снова в тиках (без пересчёта в секунды).
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText passCurrent;
    private EditText passNew;
    private EditText passConfirm;
    private CheckBox requirePassBox;

    private EditText regMailIndex;
    private EditText regPChannel;
    private EditText regPriznak;
    private EditText regIpServer;
    private EditText regIpServer2;
    private EditText regCallsign;
    private EditText regCity;

    private EditText voxPauseInput;

    private VoxView voxView;
    private TextView voxStateText;
    private TextView voxLevelText;
    private TextView rxLevelText;
    private TextView usbStatusText;
    private Button btnLockVox;

    private boolean voxLocked = false;

    private Handler handler;
    private final Runnable uiLoop = new Runnable() {
        @Override
        public void run() {
            refreshVoxUi();
            refreshUsbUi();
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_settings);

        passCurrent  = findViewById(R.id.passCurrent);
        passNew      = findViewById(R.id.passNew);
        passConfirm  = findViewById(R.id.passConfirm);
        requirePassBox = findViewById(R.id.requirePassBox);

        regMailIndex = findViewById(R.id.regMailIndex);
        regPChannel  = findViewById(R.id.regPChannel);
        regPriznak   = findViewById(R.id.regPriznak);
        regIpServer  = findViewById(R.id.regIpServer);
        regIpServer2 = findViewById(R.id.regIpServer2);
        regCallsign  = findViewById(R.id.regCallsign);
        regCity      = findViewById(R.id.regCity);

        voxView = findViewById(R.id.voxView);
        voxStateText = findViewById(R.id.voxStateText);
        voxLevelText = findViewById(R.id.voxLevelText);
        rxLevelText  = findViewById(R.id.rxLevelText);
        usbStatusText = findViewById(R.id.usbStatusText);
        voxPauseInput = findViewById(R.id.voxPauseInput);
        btnLockVox = findViewById(R.id.btnLockVox);

        Button saveBtn = findViewById(R.id.btnSaveSettings);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> saveSettings());

        Button openLogBtn = findViewById(R.id.btnOpenLog);
        if (openLogBtn != null) openLogBtn.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, LogActivity.class);
            startActivity(i);
        });

        if (btnLockVox != null) {
            btnLockVox.setOnClickListener(v -> {
                voxLocked = !voxLocked;
                voxView.setLocked(voxLocked);
                if (voxLocked) {
                    btnLockVox.setBackgroundTintList(
                            ContextCompat.getColorStateList(
                                    SettingsActivity.this, R.color.c_red));
                } else {
                    btnLockVox.setBackgroundTintList(
                            ContextCompat.getColorStateList(
                                    SettingsActivity.this, R.color.c_gray));
                }
            });
        }

        loadSettings();

        handler = new Handler(Looper.getMainLooper());
        handler.post(uiLoop);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacks(uiLoop);
    }

    private void refreshVoxUi() {
        int voxRms = 0;
        int rxRms = 0;
        boolean txActive = false;
        if (PmrService.voxEngine != null) {
            voxRms = PmrService.voxEngine.getLastRms();
            txActive = PmrService.voxEngine.isTxActive();
        }
        if (PmrService.audioEngine != null) {
            rxRms = PmrService.audioEngine.getLastRxRms();
        }

        if (voxView != null) voxView.setCurrentRms(voxRms);
        if (voxLevelText != null) voxLevelText.setText("VOX=" + voxRms);
        if (rxLevelText != null) rxLevelText.setText("RX=" + rxRms);
        if (voxStateText != null) {
            voxStateText.setText(txActive
                    ? "СОСТОЯНИЕ: ПЕРЕДАЧА"
                    : "СОСТОЯНИЕ: ГОТОВ");
        }
    }

    private void refreshUsbUi() {
        if (usbStatusText == null) return;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            usbStatusText.setText("USB-аудио: недоступно");
            return;
        }
        String name = null;
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo d : devs) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_USB_DEVICE
                    || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || t == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                CharSequence pn = d.getProductName();
                name = (pn != null) ? pn.toString() : "USB-аудио";
                break;
            }
        }
        if (name != null) {
            usbStatusText.setText("USB-аудио: ПОДКЛЮЧЕНО — " + name);
        } else {
            usbStatusText.setText("USB-аудио: НЕ ПОДКЛЮЧЕНО (используется встроенное)");
        }
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);

        boolean requirePass = sp.getBoolean(
                PasswordActivity.KEY_REQUIRE_PASSWORD, true);
        if (requirePassBox != null) requirePassBox.setChecked(requirePass);

        if (regMailIndex != null)
            regMailIndex.setText(sp.getString(
                    PasswordActivity.KEY_MY_MAIL_INDEX,
                    PasswordActivity.DEFAULT_MY_MAIL_INDEX));
        if (regPChannel != null)
            regPChannel.setText(sp.getString(
                    PasswordActivity.KEY_MY_PCHANNEL,
                    PasswordActivity.DEFAULT_MY_PCHANNEL));
        if (regPriznak != null)
            regPriznak.setText(sp.getString(
                    PasswordActivity.KEY_PRIZNAK_PMR,
                    PasswordActivity.DEFAULT_PRIZNAK_PMR));
        if (regIpServer != null)
            regIpServer.setText(sp.getString(
                    PasswordActivity.KEY_IP_SERVER,
                    PasswordActivity.DEFAULT_IP_SERVER));
        if (regIpServer2 != null)
            regIpServer2.setText(sp.getString(
                    PasswordActivity.KEY_IP_SERVER2,
                    PasswordActivity.DEFAULT_IP_SERVER2));
        if (regCallsign != null)
            regCallsign.setText(sp.getString(
                    PasswordActivity.KEY_CALLSIGN,
                    PasswordActivity.DEFAULT_CALLSIGN));
        if (regCity != null)
            regCity.setText(sp.getString(
                    PasswordActivity.KEY_CITY,
                    PasswordActivity.DEFAULT_CITY));

        if (voxView != null) {
            int myVox = sp.getInt(PasswordActivity.KEY_MY_VOX,
                    PasswordActivity.DEFAULT_MY_VOX);
            voxView.setMyVox(myVox);
        }

        /* vox_pause — тики (без пересчёта). */
        int voxPauseTicks = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                PasswordActivity.DEFAULT_VOX_PAUSE);
        if (voxPauseInput != null)
            voxPauseInput.setText(String.valueOf(voxPauseTicks));
    }

    private void saveSettings() {
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);

        boolean requirePass = requirePassBox != null && requirePassBox.isChecked();
        sp.edit().putBoolean(PasswordActivity.KEY_REQUIRE_PASSWORD,
                requirePass).apply();

        if (voxView != null) {
            sp.edit().putInt(PasswordActivity.KEY_MY_VOX, voxView.getMyVox()).apply();
        }

        /* vox_pause в тиках. */
        if (voxPauseInput != null) {
            try {
                int ticks = Integer.parseInt(voxPauseInput.getText().toString().trim());
                if (ticks < 1) ticks = 1;
                if (ticks > 1000) ticks = 1000;
                sp.edit().putInt(PasswordActivity.KEY_VOX_PAUSE, ticks).apply();
            } catch (NumberFormatException ignored) {}
        }

        String cur = sp.getString(PasswordActivity.KEY_PASSWORD,
                PasswordActivity.DEFAULT_PASSWORD);
        String enteredCur = (passCurrent != null)
                ? passCurrent.getText().toString() : "";
        String newPass = (passNew != null)
                ? passNew.getText().toString() : "";
        String confirmPass = (passConfirm != null)
                ? passConfirm.getText().toString() : "";

        if (!enteredCur.isEmpty() || !newPass.isEmpty() || !confirmPass.isEmpty()) {
            if (!cur.equals(enteredCur)) {
                Toast.makeText(this, R.string.settings_error_current,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPass.isEmpty()) {
                Toast.makeText(this, R.string.settings_error_empty,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, R.string.settings_error_mismatch,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            sp.edit().putString(PasswordActivity.KEY_PASSWORD, newPass).apply();
        }

        String myMailIndex = (regMailIndex != null)
                ? regMailIndex.getText().toString().trim() : "";
        String myPChannel = (regPChannel != null)
                ? regPChannel.getText().toString().trim() : "";
        String priznak = (regPriznak != null)
                ? regPriznak.getText().toString().trim() : "";
        String ipServer = (regIpServer != null)
                ? regIpServer.getText().toString().trim() : "";
        String ipServer2 = (regIpServer2 != null)
                ? regIpServer2.getText().toString().trim() : "";
        String callsign = (regCallsign != null)
                ? regCallsign.getText().toString().trim() : "";
        String city = (regCity != null)
                ? regCity.getText().toString().trim() : "";

        if (!myMailIndex.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_MY_MAIL_INDEX, myMailIndex).apply();
        if (!myPChannel.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_MY_PCHANNEL, myPChannel).apply();
        if (!priznak.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_PRIZNAK_PMR, priznak).apply();
        if (!ipServer.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_IP_SERVER, ipServer).apply();
        if (!ipServer2.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_IP_SERVER2, ipServer2).apply();
        if (!callsign.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_CALLSIGN, callsign).apply();
        if (!city.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_CITY, city).apply();

        if (PmrService.pmrSocket != null) {
            PmrService.pmrSocket.reloadFromPrefs(this);
        }

        Toast.makeText(this, R.string.settings_saved,
                Toast.LENGTH_SHORT).show();

        if (passCurrent != null) passCurrent.setText("");
        if (passNew != null) passNew.setText("");
        if (passConfirm != null) passConfirm.setText("");
    }
}