package com.pmr.admin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/* Экран ввода пароля Android Link PMR V4.0.1.
 *
 * Изменения V4.0.1:
 *   - новые дефолты: Priznak_pmr=11111, MyMailIndex=11111, MyPChannel=4,
 *     ip_server=192.168.0.1, password=12345;
 *   - My_Vox=1000, onUsilMic=1, onUsilDin=1, ptt_tone_level=0;
 *   - новые ключи: KEY_PTT_TONE_HZ, KEY_RX_GAIN, KEY_LOG_EMAIL;
 *   - удалён KEY_CHECK_SYSTEM — экран проверки системы убран.
 */
public class PasswordActivity extends AppCompatActivity {

    public static final String PREFS = "admin_pmr_prefs";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_REFRESH = "refresh_sec";
    public static final String KEY_REQUIRE_PASSWORD = "require_password";
    public static final String KEY_PORT_PRM = "port_prm";
    public static final String KEY_PORT_PRD = "port_prd";
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

    /* Частота PTT-тона (100..3000 Гц). */
    public static final String KEY_PTT_TONE_HZ = "ptt_tone_hz";

    /* Усиление правого канала (R) — приём (0..100). */
    public static final String KEY_RX_GAIN = "rx_gain";

    /* Email для отправки логов. */
    public static final String KEY_LOG_EMAIL = "log_email";

    /* Усиление микрофона. */
    public static final String KEY_USIL_MIC   = "on_usil_mic";
    public static final String KEY_MIC_USIL   = "mic_usil";

    /* Усиление приёмного тракта. */
    public static final String KEY_USIL_DIN   = "on_usil_din";
    public static final String KEY_DIN_USIL   = "din_usil";

    public static final String DEFAULT_PASSWORD = "12345";
    public static final int    DEFAULT_REFRESH = 2;
    public static final int    DEFAULT_PORT_PRM = 5354;
    public static final int    DEFAULT_PORT_PRD = 16000;

    public static final String DEFAULT_MY_MAIL_INDEX = "11111";
    public static final String DEFAULT_MY_PCHANNEL   = "4";
    public static final String DEFAULT_PRIZNAK_PMR   = "11111";
    public static final String DEFAULT_IP_SERVER     = "192.168.0.1";
    public static final String DEFAULT_CALLSIGN      = "RW6HHL";
    public static final String DEFAULT_CITY          = "Мин-Воды";

    public static final int     DEFAULT_MY_VOX     = 1000;
    public static final int     DEFAULT_VOX_PAUSE  = 30;
    public static final int     DEFAULT_VOX_MIC    = 100;
    public static final int     DEFAULT_POSLE_PRD  = 3;
    public static final boolean DEFAULT_VOX_LOCKED = false;

    /* Уровень тона 1000 Гц: 0..100 %, по умолчанию 0 % (пользователь настроит вручную). */
    public static final int     DEFAULT_PTT_TONE_LEVEL = 0;

    /* Частота тона: 100..3000 Гц, по умолчанию 1000 Гц. */
    public static final int     DEFAULT_PTT_TONE_HZ = 1000;

    /* Усиление правого канала: 0..100, по умолчанию 50 (1.0x). */
    public static final int     DEFAULT_RX_GAIN = 50;

    /* Email для логов. */
    public static final String  DEFAULT_LOG_EMAIL = "qrz@mail.ru";

    /* Усиления: 0 = выключено, 1 = включено. */
    public static final int    DEFAULT_USIL_MIC = 1;
    public static final double DEFAULT_MIC_USIL = 2.0;
    public static final int    DEFAULT_USIL_DIN = 1;
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