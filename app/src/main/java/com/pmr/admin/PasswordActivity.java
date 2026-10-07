package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/* Экран ввода пароля Android Link PMR V3.0.
 *
 * Изменения V3.0:
 *   - добавлен KEY_PTT_TONE_LEVEL / DEFAULT_PTT_TONE_LEVEL —
 *     уровень тона PTT 1000 Гц (0..100 %), по умолчанию 50.
 */
public class PasswordActivity extends AppCompatActivity {

    public static final String PREFS = "admin_pmr_prefs";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_REFRESH = "refresh_sec";
    public static final String KEY_REQUIRE_PASSWORD = "require_password";
    public static final String KEY_PORT_PRM = "port_prm";
    public static final String KEY_PORT_PRD = "port_prd";
    public static final String KEY_CHECK_SYSTEM = "check_system";
    public static final String KEY_26_STATE = "state_26";

    public static final String KEY_MY_MAIL_INDEX = "my_mail_index";
    public static final String KEY_MY_PCHANNEL   = "my_pchannel";
    public static final String KEY_PRIZNAK_PMR   = "priznak_pmr";
    public static final String KEY_IP_SERVER     = "ip_server";
    public static final String KEY_CALLSIGN      = "callsign";
    public static final String KEY_CITY          = "city";

    public static final String KEY_MY_VOX     = "my_vox";
    public static final String KEY_VOX_PAUSE  = "vox_pause";
    public static final String KEY_VOX_MIC    = "vox_mic";
    public static final String KEY_POSLE_PRD  = "posle_prd";
    public static final String KEY_VOX_LOCKED = "vox_locked";

    /* Уровень PTT-тона (0..100 %). */
    public static final String KEY_PTT_TONE_LEVEL = "ptt_tone_level";

    /* Усиление микрофона. */
    public static final String KEY_USIL_MIC   = "on_usil_mic";
    public static final String KEY_MIC_USIL   = "mic_usil";

    /* Усиление приёмного тракта. */
    public static final String KEY_USIL_DIN   = "on_usil_din";
    public static final String KEY_DIN_USIL   = "din_usil";

    public static final String DEFAULT_PASSWORD = "Rostov2026";
    public static final int    DEFAULT_REFRESH = 2;
    public static final int    DEFAULT_PORT_PRM = 5354;
    public static final int    DEFAULT_PORT_PRD = 16000;

    public static final String DEFAULT_MY_MAIL_INDEX = "51953";
    public static final String DEFAULT_MY_PCHANNEL   = "4";
    public static final String DEFAULT_PRIZNAK_PMR   = "26005";
    public static final String DEFAULT_IP_SERVER     = "185.221.154.39";
    public static final String DEFAULT_CALLSIGN      = "RW6HHL";
    public static final String DEFAULT_CITY          = "Мин-Воды";

    public static final int     DEFAULT_MY_VOX     = 400;
    public static final int     DEFAULT_VOX_PAUSE  = 30;
    public static final int     DEFAULT_VOX_MIC    = 100;
    public static final int     DEFAULT_POSLE_PRD  = 3;
    public static final boolean DEFAULT_VOX_LOCKED = false;

    /* Уровень тона 1000 Гц: 0..100 %, по умолчанию 50 %. */
    public static final int     DEFAULT_PTT_TONE_LEVEL = 50;

    /* Усиления: 0 = выключено, 1 = включено. */
    public static final int    DEFAULT_USIL_MIC = 0;
    public static final double DEFAULT_MIC_USIL = 2.0;
    public static final int    DEFAULT_USIL_DIN = 0;
    public static final double DEFAULT_DIN_USIL = 0.4;

    private EditText passInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_password);

        passInput = findViewById(R.id.passInput);
        Button passBtn = findViewById(R.id.passBtn);

        if (passBtn != null) {
            passBtn.setOnClickListener(v -> checkPassword());
        }
    }

    private void checkPassword() {
        if (passInput == null) return;
        String entered = passInput.getText().toString();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = sp.getString(KEY_PASSWORD, DEFAULT_PASSWORD);

        if (saved.equals(entered)) {
            Intent i = new Intent(PasswordActivity.this, MainActivity.class);
            startActivity(i);
            finish();
        } else {
            Toast.makeText(this, R.string.password_error,
                    Toast.LENGTH_SHORT).show();
            passInput.setText("");
        }
    }
}