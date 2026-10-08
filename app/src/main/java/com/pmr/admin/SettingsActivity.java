package com.pmr.admin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

/* Экран настроек Android Link PMR V4.0.1.
 *
 * Изменения V4.0.1:
 *   - добавлены поля ptt_tone_hz (100..3000), rx_gain (0..100), log_email;
 *   - заголовок экрана — «НАСТРОЙКИ V4.0.1» (из strings.xml).
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
    private EditText regPortPrm;
    private EditText regPortPrd;
    private EditText regCallsign;
    private EditText regCity;
    private EditText logEmailInput;

    private EditText etOnUsilMic;
    private EditText etMicUsil;
    private EditText etOnUsilDin;
    private EditText etDinUsil;

    private EditText voxPauseInput;
    private EditText voxMicInput;
    private EditText poslePrdInput;
    private EditText pttToneLevelInput;
    private EditText pttToneHzInput;
    private EditText rxGainInput;

    private VoxView voxView;
    private TextView voxStateText;
    private TextView pttStateText;
    private TextView voxLevelText;
    private TextView maxLevelText;
    private TextView rxLevelText;
    private TextView myVoxText;
    private Button btnLockVox;

    private boolean voxLocked = false;

    private Handler handler;
    private final Runnable uiLoop = new Runnable() {
        @Override
        public void run() {
            refreshVoxUi();
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
        regPortPrm   = findViewById(R.id.regPortPrm);
        regPortPrd   = findViewById(R.id.regPortPrd);
        regCallsign  = findViewById(R.id.regCallsign);
        regCity      = findViewById(R.id.regCity);
        logEmailInput = findViewById(R.id.logEmailInput);

        etOnUsilMic = findViewById(R.id.etOnUsilMic);
        etMicUsil   = findViewById(R.id.etMicUsil);
        etOnUsilDin = findViewById(R.id.etOnUsilDin);
        etDinUsil   = findViewById(R.id.etDinUsil);

        voxView = findViewById(R.id.voxView);
        voxStateText = findViewById(R.id.voxStateText);
        pttStateText = findViewById(R.id.pttStateText);
        voxLevelText = findViewById(R.id.voxLevelText);
        maxLevelText = findViewById(R.id.maxLevelText);
        rxLevelText  = findViewById(R.id.rxLevelText);
        myVoxText    = findViewById(R.id.myVoxText);
        voxPauseInput = findViewById(R.id.voxPauseInput);
        voxMicInput   = findViewById(R.id.voxMicInput);
        poslePrdInput = findViewById(R.id.poslePrdInput);
        pttToneLevelInput = findViewById(R.id.pttToneLevelInput);
        pttToneHzInput = findViewById(R.id.pttToneHzInput);
        rxGainInput   = findViewById(R.id.rxGainInput);
        btnLockVox = findViewById(R.id.btnLockVox);

        Button saveBtn = findViewById(R.id.btnSaveSettings);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> saveSettings());

        Button openLogBtn = findViewById(R.id.btnOpenLog);
        if (openLogBtn != null) openLogBtn.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, LogActivity.class);
            startActivity(i);
        });

        if (voxView != null) {
            voxView.setListener(myVox -> {
                SharedPreferences sp = getSharedPreferences(
                        PasswordActivity.PREFS, MODE_PRIVATE);
                sp.edit().putInt(PasswordActivity.KEY_MY_VOX, myVox).apply();
            });
        }

        if (btnLockVox != null) {
            btnLockVox.setOnClickListener(v -> {
                voxLocked = !voxLocked;
                voxView.setLocked(voxLocked);
                SharedPreferences sp = getSharedPreferences(
                        PasswordActivity.PREFS, MODE_PRIVATE);
                sp.edit().putBoolean(PasswordActivity.KEY_VOX_LOCKED,
                        voxLocked).apply();
                sp.edit().putInt(PasswordActivity.KEY_MY_VOX,
                        voxView.getMyVox()).apply();
                updateLockButtonUi();
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

    private void updateLockButtonUi() {
        if (btnLockVox == null) return;
        if (voxLocked) {
            btnLockVox.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                            SettingsActivity.this, R.color.c_red));
        } else {
            btnLockVox.setBackgroundTintList(
                    ContextCompat.getColorStateList(
                            SettingsActivity.this, R.color.c_gray));
        }
    }

    private void refreshVoxUi() {
        int voxRms = 0;
        int maxRms = 0;
        int rxRms = 0;
        boolean txActive = false;
        boolean rxActive = false;
        if (PmrService.voxEngine != null) {
            voxRms = PmrService.voxEngine.getLastRms();
            maxRms = PmrService.voxEngine.getMaxRms3Sec();
            txActive = PmrService.voxEngine.isTxActive();
        }
        if (PmrService.audioEngine != null) {
            rxRms = PmrService.audioEngine.getLastRxRms();
            rxActive = PmrService.audioEngine.isRxActive();
        }

        if (voxView != null) voxView.setCurrentRms(voxRms);
        if (voxLevelText != null) voxLevelText.setText("VOX=" + voxRms);
        if (maxLevelText != null) maxLevelText.setText("MAX=" + maxRms);
        if (rxLevelText != null) rxLevelText.setText("RX=" + rxRms);
        if (myVoxText != null && voxView != null)
            myVoxText.setText("My_Vox=" + voxView.getMyVox());

        if (voxStateText != null) {
            String state;
            if (rxActive) {
                state = "СОСТОЯНИЕ: ПРИЕМ";
            } else if (txActive) {
                state = "СОСТОЯНИЕ: ПЕРЕДАЧА";
            } else {
                state = "СОСТОЯНИЕ: ГОТОВ";
            }
            voxStateText.setText(state);
        }

        if (pttStateText != null) {
            pttStateText.setText(rxActive ? "PTT: ВКЛ" : "PTT: ВЫКЛ");
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
        if (regPortPrm != null)
            regPortPrm.setText(String.valueOf(sp.getInt(
                    PasswordActivity.KEY_PORT_PRM,
                    PasswordActivity.DEFAULT_PORT_PRM)));
        if (regPortPrd != null)
            regPortPrd.setText(String.valueOf(sp.getInt(
                    PasswordActivity.KEY_PORT_PRD,
                    PasswordActivity.DEFAULT_PORT_PRD)));
        if (regCallsign != null)
            regCallsign.setText(sp.getString(
                    PasswordActivity.KEY_CALLSIGN,
                    PasswordActivity.DEFAULT_CALLSIGN));
        if (regCity != null)
            regCity.setText(sp.getString(
                    PasswordActivity.KEY_CITY,
                    PasswordActivity.DEFAULT_CITY));
        if (logEmailInput != null)
            logEmailInput.setText(sp.getString(
                    PasswordActivity.KEY_LOG_EMAIL,
                    PasswordActivity.DEFAULT_LOG_EMAIL));

        if (etOnUsilMic != null)
            etOnUsilMic.setText(String.valueOf(sp.getInt(
                    PasswordActivity.KEY_USIL_MIC,
                    PasswordActivity.DEFAULT_USIL_MIC)));
        if (etMicUsil != null) {
            double micUsil = PasswordActivity.DEFAULT_MIC_USIL;
            String s = sp.getString(PasswordActivity.KEY_MIC_USIL, null);
            if (s != null) {
                try { micUsil = Double.parseDouble(s); } catch (Exception ignored) {}
            }
            etMicUsil.setText(String.valueOf(micUsil));
        }
        if (etOnUsilDin != null)
            etOnUsilDin.setText(String.valueOf(sp.getInt(
                    PasswordActivity.KEY_USIL_DIN,
                    PasswordActivity.DEFAULT_USIL_DIN)));
        if (etDinUsil != null) {
            double dinUsil = PasswordActivity.DEFAULT_DIN_USIL;
            String s = sp.getString(PasswordActivity.KEY_DIN_USIL, null);
            if (s != null) {
                try { dinUsil = Double.parseDouble(s); } catch (Exception ignored) {}
            }
            etDinUsil.setText(String.valueOf(dinUsil));
        }

        if (voxView != null) {
            int myVox = sp.getInt(PasswordActivity.KEY_MY_VOX,
                    PasswordActivity.DEFAULT_MY_VOX);
            voxView.setMyVox(myVox);
        }

        voxLocked = sp.getBoolean(PasswordActivity.KEY_VOX_LOCKED,
                PasswordActivity.DEFAULT_VOX_LOCKED);
        if (voxView != null) voxView.setLocked(voxLocked);
        updateLockButtonUi();

        int voxPauseTicks = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                PasswordActivity.DEFAULT_VOX_PAUSE);
        if (voxPauseInput != null)
            voxPauseInput.setText(String.valueOf(voxPauseTicks));

        int voxMic = sp.getInt(PasswordActivity.KEY_VOX_MIC,
                PasswordActivity.DEFAULT_VOX_MIC);
        if (voxMicInput != null)
            voxMicInput.setText(String.valueOf(voxMic));

        int poslePrd = sp.getInt(PasswordActivity.KEY_POSLE_PRD,
                PasswordActivity.DEFAULT_POSLE_PRD);
        if (poslePrdInput != null)
            poslePrdInput.setText(String.valueOf(poslePrd));

        int pttToneLevel = sp.getInt(PasswordActivity.KEY_PTT_TONE_LEVEL,
                PasswordActivity.DEFAULT_PTT_TONE_LEVEL);
        if (pttToneLevelInput != null)
            pttToneLevelInput.setText(String.valueOf(pttToneLevel));

        int pttToneHz = sp.getInt(PasswordActivity.KEY_PTT_TONE_HZ,
                PasswordActivity.DEFAULT_PTT_TONE_HZ);
        if (pttToneHzInput != null)
            pttToneHzInput.setText(String.valueOf(pttToneHz));

        int rxGain = sp.getInt(PasswordActivity.KEY_RX_GAIN,
                PasswordActivity.DEFAULT_RX_GAIN);
        if (rxGainInput != null)
            rxGainInput.setText(String.valueOf(rxGain));
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

        if (voxPauseInput != null) {
            try {
                int ticks = Integer.parseInt(voxPauseInput.getText().toString().trim());
                if (ticks < 1) ticks = 1;
                if (ticks > 1000) ticks = 1000;
                sp.edit().putInt(PasswordActivity.KEY_VOX_PAUSE, ticks).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (voxMicInput != null) {
            try {
                int v = Integer.parseInt(voxMicInput.getText().toString().trim());
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                sp.edit().putInt(PasswordActivity.KEY_VOX_MIC, v).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (poslePrdInput != null) {
            try {
                int v = Integer.parseInt(poslePrdInput.getText().toString().trim());
                if (v < 0) v = 0;
                if (v > 1000) v = 1000;
                sp.edit().putInt(PasswordActivity.KEY_POSLE_PRD, v).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (pttToneLevelInput != null) {
            try {
                int v = Integer.parseInt(pttToneLevelInput.getText().toString().trim());
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                sp.edit().putInt(PasswordActivity.KEY_PTT_TONE_LEVEL, v).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (pttToneHzInput != null) {
            try {
                int v = Integer.parseInt(pttToneHzInput.getText().toString().trim());
                if (v < 100) v = 100;
                if (v > 3000) v = 3000;
                sp.edit().putInt(PasswordActivity.KEY_PTT_TONE_HZ, v).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (rxGainInput != null) {
            try {
                int v = Integer.parseInt(rxGainInput.getText().toString().trim());
                if (v < 0) v = 0;
                if (v > 100) v = 100;
                sp.edit().putInt(PasswordActivity.KEY_RX_GAIN, v).apply();
            } catch (NumberFormatException ignored) {}
        }

        if (etOnUsilMic != null) {
            try {
                int v = Integer.parseInt(etOnUsilMic.getText().toString().trim());
                if (v != 0 && v != 1) v = 0;
                sp.edit().putInt(PasswordActivity.KEY_USIL_MIC, v).apply();
            } catch (NumberFormatException ignored) {}
        }
        if (etMicUsil != null) {
            try {
                double v = Double.parseDouble(etMicUsil.getText().toString().trim());
                sp.edit().putString(PasswordActivity.KEY_MIC_USIL,
                        String.valueOf(v)).apply();
            } catch (NumberFormatException ignored) {}
        }
        if (etOnUsilDin != null) {
            try {
                int v = Integer.parseInt(etOnUsilDin.getText().toString().trim());
                if (v != 0 && v != 1) v = 0;
                sp.edit().putInt(PasswordActivity.KEY_USIL_DIN, v).apply();
            } catch (NumberFormatException ignored) {}
        }
        if (etDinUsil != null) {
            try {
                double v = Double.parseDouble(etDinUsil.getText().toString().trim());
                sp.edit().putString(PasswordActivity.KEY_DIN_USIL,
                        String.valueOf(v)).apply();
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
        String callsign = (regCallsign != null)
                ? regCallsign.getText().toString().trim() : "";
        String city = (regCity != null)
                ? regCity.getText().toString().trim() : "";
        String email = (logEmailInput != null)
                ? logEmailInput.getText().toString().trim() : "";

        if (!myMailIndex.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_MY_MAIL_INDEX, myMailIndex).apply();
        if (!myPChannel.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_MY_PCHANNEL, myPChannel).apply();
        if (!priznak.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_PRIZNAK_PMR, priznak).apply();
        if (!ipServer.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_IP_SERVER, ipServer).apply();
        if (!callsign.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_CALLSIGN, callsign).apply();
        if (!city.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_CITY, city).apply();
        if (!email.isEmpty()) sp.edit().putString(
                PasswordActivity.KEY_LOG_EMAIL, email).apply();

        if (regPortPrm != null) {
            try {
                int v = Integer.parseInt(regPortPrm.getText().toString().trim());
                if (v > 0 && v < 65536)
                    sp.edit().putInt(PasswordActivity.KEY_PORT_PRM, v).apply();
            } catch (NumberFormatException ignored) {}
        }
        if (regPortPrd != null) {
            try {
                int v = Integer.parseInt(regPortPrd.getText().toString().trim());
                if (v > 0 && v < 65536)
                    sp.edit().putInt(PasswordActivity.KEY_PORT_PRD, v).apply();
            } catch (NumberFormatException ignored) {}
        }

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