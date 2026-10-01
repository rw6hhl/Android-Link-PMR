package com.pmr.admin;

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

/* Экран настроек Android Link PMR V1.1. */
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

    private VoxView voxView;
    private TextView voxStateText;

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
        regIpServer2 = findViewById(R.id.regIpServer2);
        regCallsign  = findViewById(R.id.regCallsign);
        regCity      = findViewById(R.id.regCity);

        voxView = findViewById(R.id.voxView);
        voxStateText = findViewById(R.id.voxStateText);

        Button saveBtn = findViewById(R.id.btnSaveSettings);
        if (saveBtn != null) saveBtn.setOnClickListener(v -> saveSettings());

        Button openLogBtn = findViewById(R.id.btnOpenLog);
        if (openLogBtn != null) openLogBtn.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, LogActivity.class);
            startActivity(i);
        });

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
        if (voxView == null) return;
        if (PmrService.voxEngine != null) {
            voxView.setCurrentRms(PmrService.voxEngine.getLastRms());
            if (voxStateText != null) {
                voxStateText.setText(PmrService.voxEngine.isTxActive()
                        ? "СОСТОЯНИЕ: ПЕРЕДАЧА"
                        : "СОСТОЯНИЕ: ГОТОВ");
            }
        } else {
            voxView.setCurrentRms(0);
            if (voxStateText != null) voxStateText.setText("СОСТОЯНИЕ: —");
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
            int voxPause = sp.getInt(PasswordActivity.KEY_VOX_PAUSE,
                    PasswordActivity.DEFAULT_VOX_PAUSE);
            voxView.setMyVox(myVox);
            voxView.setVoxPause(voxPause);
        }
    }

    private void saveSettings() {
        SharedPreferences sp = getSharedPreferences(
                PasswordActivity.PREFS, MODE_PRIVATE);

        boolean requirePass = requirePassBox != null && requirePassBox.isChecked();
        sp.edit().putBoolean(PasswordActivity.KEY_REQUIRE_PASSWORD,
                requirePass).apply();

        if (voxView != null) {
            sp.edit()
                    .putInt(PasswordActivity.KEY_MY_VOX, voxView.getMyVox())
                    .putInt(PasswordActivity.KEY_VOX_PAUSE, voxView.getVoxPause())
                    .apply();
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