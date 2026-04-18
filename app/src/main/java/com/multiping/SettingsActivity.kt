package com.multiping

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.multiping.data.CheckMode
import com.multiping.notification.NotificationHelper
import com.multiping.service.PingService

class SettingsActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    companion object {
        const val BATTERY_MODE_ALL_ECO       = 0
        const val BATTERY_MODE_PAUSE         = 1
        const val BATTERY_MODE_FAVS_INTERVAL = 2
        const val BATTERY_MODE_ALL_INTERVAL  = 3
        const val KEY_BATTERY_MODE           = "battery_mode"
        const val KEY_SHOW_ON_LOCK_SCREEN    = "show_on_lock_screen"
        const val KEY_ALERT_SOUND_URI        = "alert_sound_uri"   // пустая строка = дефолт, "none" = тихо
    }

    private var currentBatteryMode = BATTERY_MODE_ALL_ECO
    private var selectedSoundUri: String? = null   // null = ещё не трогали

    // Лаунчер для системного RingtonePicker
    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedSoundUri = uri?.toString() ?: "none"
            updateSoundLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        val swNotification   = findViewById<Switch>(R.id.swShowNotification)
        val swLockScreen     = findViewById<Switch>(R.id.swShowOnLockScreen)
        val swAllEco         = findViewById<Switch>(R.id.swBatteryAllEco)
        val swPause          = findViewById<Switch>(R.id.swPauseOnScreenOff)
        val swFavsInterval   = findViewById<Switch>(R.id.swMonitorFavsScreenOff)
        val swAllInterval    = findViewById<Switch>(R.id.swBatteryAllInterval)
        val tvFavsWarning    = findViewById<TextView>(R.id.tvFavsWarning)
        val tvCurrentSound   = findViewById<TextView>(R.id.tvCurrentSound)
        val btnChooseSound   = findViewById<Button>(R.id.btnChooseSound)
        val sbGreen          = findViewById<SeekBar>(R.id.sbGreenThreshold)
        val sbRed            = findViewById<SeekBar>(R.id.sbRedThreshold)
        val tvGreen          = findViewById<TextView>(R.id.tvGreenValue)
        val tvRed            = findViewById<TextView>(R.id.tvRedValue)
        val etDefInterval    = findViewById<EditText>(R.id.etDefInterval)
        val etDefTimeout     = findViewById<EditText>(R.id.etDefTimeout)
        val etDefAvgWindow   = findViewById<EditText>(R.id.etDefAvgWindow)
        val rgDefCheckMode   = findViewById<RadioGroup>(R.id.rgDefCheckMode)
        val btnSave          = findViewById<Button>(R.id.btnSaveSettings)

        // Пары Switch → mode
        val options = listOf(
            swAllEco       to BATTERY_MODE_ALL_ECO,
            swPause        to BATTERY_MODE_PAUSE,
            swFavsInterval to BATTERY_MODE_FAVS_INTERVAL,
            swAllInterval  to BATTERY_MODE_ALL_INTERVAL
        )

        // ── Загружаем сохранённые значения ──────────────────────────────
        swNotification.isChecked = vm.prefs.getBoolean(PingService.KEY_SHOW_NOTIFICATION, true)
        swLockScreen.isChecked   = vm.prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, true)
        currentBatteryMode       = vm.prefs.getInt(KEY_BATTERY_MODE, BATTERY_MODE_ALL_ECO)
        selectedSoundUri         = vm.prefs.getString(KEY_ALERT_SOUND_URI, "")

        renderSwitches(options, tvFavsWarning)
        updateSoundLabel(tvCurrentSound)

        // ── Батарея — RadioGroup логика ─────────────────────────────────
        options.forEach { (sw, mode) ->
            attachListener(sw, mode, options, tvFavsWarning)
        }

        // ── Выбор звука ─────────────────────────────────────────────────
        btnChooseSound.setOnClickListener {
            val currentUri = when (val s = selectedSoundUri) {
                null, "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "none"   -> null
                else     -> Uri.parse(s)
            }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.settings_alert_sound))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                if (currentUri != null)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            ringtoneLauncher.launch(intent)
        }

        // ── Светофор ────────────────────────────────────────────────────
        sbGreen.max = 100; sbRed.max = 100
        sbGreen.progress = vm.greenThreshold()
        sbRed.progress   = vm.redThreshold()
        fun updateLabels() {
            tvGreen.text = getString(R.string.settings_green, sbGreen.progress)
            tvRed.text   = getString(R.string.settings_red,   sbRed.progress)
        }
        updateLabels()
        sbGreen.setOnSeekBarChangeListener(seekListener { updateLabels() })
        sbRed.setOnSeekBarChangeListener(seekListener   { updateLabels() })

        // ── Дефолты ─────────────────────────────────────────────────────
        etDefInterval.setText(vm.defaultInterval().toString())
        etDefTimeout.setText(vm.defaultTimeout().toString())
        etDefAvgWindow.setText(vm.defaultAvgWindow().toString())

        // Дефолтный тип опроса
        rgDefCheckMode.check(when (vm.defaultCheckMode()) {
            CheckMode.HTTP -> R.id.rbDefHttp
            CheckMode.TCP  -> R.id.rbDefTcp
            CheckMode.PING -> R.id.rbDefPing
        })

        // ── Сохранить ───────────────────────────────────────────────────
        btnSave.setOnClickListener {
            val green = sbGreen.progress; val red = sbRed.progress
            if (red >= green) {
                Toast.makeText(this, R.string.settings_threshold_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pauseOn = currentBatteryMode == BATTERY_MODE_PAUSE
            val favsOn  = currentBatteryMode == BATTERY_MODE_FAVS_INTERVAL

            vm.prefs.edit()
                .putBoolean(PingService.KEY_SHOW_NOTIFICATION,       swNotification.isChecked)
                .putBoolean(KEY_SHOW_ON_LOCK_SCREEN,                 swLockScreen.isChecked)
                .putBoolean(PingService.KEY_PAUSE_ON_SCREEN_OFF,     pauseOn)
                .putBoolean(PingService.KEY_MONITOR_FAVS_SCREEN_OFF, favsOn)
                .putInt(KEY_BATTERY_MODE,                            currentBatteryMode)
                .putString(KEY_ALERT_SOUND_URI,                      selectedSoundUri ?: "")
                .apply()

            // Применяем новый звук (создаём канал с уникальным ID)
            NotificationHelper.applyNewAlertSound(this)

            vm.saveThresholds(green, red)
            val defCheckMode = when (rgDefCheckMode.checkedRadioButtonId) {
                R.id.rbDefHttp -> CheckMode.HTTP
                R.id.rbDefTcp  -> CheckMode.TCP
                else           -> CheckMode.PING
            }
            vm.saveDefaults(
                (etDefInterval.text.toString().toIntOrNull()  ?: 5).coerceIn(1, 3600),
                (etDefTimeout.text.toString().toIntOrNull()   ?: 3).coerceIn(1, 60),
                (etDefAvgWindow.text.toString().toIntOrNull() ?: 60).coerceIn(0, 3600),
                defCheckMode
            )
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ── Звук ──────────────────────────────────────────────────────────────
    private fun updateSoundLabel(tv: TextView? = null) {
        val label = tv ?: findViewById<TextView>(R.id.tvCurrentSound) ?: return
        label.text = when (selectedSoundUri) {
            null, "" -> getString(R.string.alert_sound_default)
            "none"   -> getString(R.string.alert_sound_none)
            else     -> {
                // Получаем название мелодии
                try {
                    val uri = Uri.parse(selectedSoundUri)
                    val ringtone = android.media.RingtoneManager.getRingtone(this, uri)
                    ringtone?.getTitle(this) ?: getString(R.string.alert_sound_custom)
                } catch (e: Exception) {
                    getString(R.string.alert_sound_custom)
                }
            }
        }
    }

    // ── Батарея RadioGroup ─────────────────────────────────────────────────
    private fun renderSwitches(options: List<Pair<Switch, Int>>, warning: TextView) {
        options.forEach { (sw, mode) ->
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = (mode == currentBatteryMode)
        }
        options.forEach { (sw, mode) -> attachListener(sw, mode, options, warning) }
        warning.visibility = if (currentBatteryMode == BATTERY_MODE_FAVS_INTERVAL
            || currentBatteryMode == BATTERY_MODE_ALL_INTERVAL) View.VISIBLE else View.GONE
    }

    private fun attachListener(sw: Switch, mode: Int, options: List<Pair<Switch, Int>>, warning: TextView) {
        sw.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && currentBatteryMode == mode) {
                // Запрещаем выключать активный
                sw.setOnCheckedChangeListener(null)
                sw.isChecked = true
                sw.post { attachListener(sw, mode, options, warning) }
                return@setOnCheckedChangeListener
            }
            if (isChecked && currentBatteryMode != mode) {
                currentBatteryMode = mode
                renderSwitches(options, warning)
            }
        }
    }

    private fun seekListener(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { onChange() }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
