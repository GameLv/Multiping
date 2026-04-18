package com.multiping

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.multiping.service.PingService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var adapter: HostAdapter
    private var serviceRunning = true

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startPingService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLanguage()
        setContentView(R.layout.activity_main)

        setupDonateButton()
        setupStartStop()
        setupSettingsButton()
        setupLanguageButton()
        setupSearch()
        setupRecyclerView()
        setupFab()
        observeHosts()
        requestNotificationPermissionIfNeeded()
    }

    // ── Донат ─────────────────────────────────────────────────────────────
    private fun setupDonateButton() {
        findViewById<ImageView>(R.id.btnDonate).setOnClickListener {
            showDonateDialog()
        }
    }

    private fun showDonateDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_donate)

        // Прозрачный системный фон — наш bg_dialog рисует свой
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.btnCloseDonate).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    // ── Старт / Стоп ──────────────────────────────────────────────────────
    private fun setupStartStop() {
        val btn = findViewById<ImageView>(R.id.btnStartStop)
        updateStartStopIcon(btn)
        btn.setOnClickListener {
            if (serviceRunning) {
                stopService(PingService.stopIntent(this))
                serviceRunning = false
            } else {
                startPingService()
                serviceRunning = true
            }
            updateStartStopIcon(btn)
        }
    }

    private fun updateStartStopIcon(btn: ImageView) {
        btn.setImageResource(
            if (serviceRunning) R.drawable.ic_stop_square else R.drawable.ic_play
        )
        btn.contentDescription = getString(
            if (serviceRunning) R.string.btn_stop else R.string.btn_start
        )
    }

    // ── Настройки ─────────────────────────────────────────────────────────
    private fun setupSettingsButton() {
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── Язык ──────────────────────────────────────────────────────────────
    private fun setupLanguageButton() {
        val langCodes = listOf("ru","en","de","fr","es","pt","zh","ko","ja","ba","kk","ar")
        val langNames = arrayOf(
            "🇷🇺  Русский",
            "🇬🇧  English",
            "🇩🇪  Deutsch",
            "🇫🇷  Français",
            "🇪🇸  Español",
            "🇧🇷  Português",
            "🇨🇳  中文",
            "🇰🇷  한국어",
            "🇯🇵  日本語",
            "🇷🇺  Башҡортса",
            "🇰🇿  Қазақша",
            "🇸🇦  العربية"
        )
        findViewById<ImageView>(R.id.btnLanguage).setOnClickListener {
            val current = vm.prefs.getString(PingService.KEY_LANGUAGE, "ru") ?: "ru"
            val checked = langCodes.indexOf(current).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.lang_dialog_title))
                .setSingleChoiceItems(langNames, checked) { dialog, which ->
                    val lang = langCodes[which]
                    vm.prefs.edit().putString(PingService.KEY_LANGUAGE, lang).apply()
                    dialog.dismiss()
                    recreate()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    // ── Поиск ─────────────────────────────────────────────────────────────
    private fun setupSearch() {
        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                vm.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ── RecyclerView ──────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = HostAdapter(
            appContext  = applicationContext,
            onEdit      = { host -> showAddSheet(host) },
            onDelete    = { host ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_confirm_title, host.address))
                    .setPositiveButton(R.string.delete_confirm_yes) { _, _ -> vm.deleteHost(host) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onToggle    = { host, enabled -> vm.setEnabled(host, enabled) },
            onFavourite = { host -> vm.toggleFavourite(host) }
        )
        findViewById<RecyclerView>(R.id.rvHosts).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter        = this@MainActivity.adapter
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            if (vm.atLimit) Toast.makeText(this, R.string.limit_reached, Toast.LENGTH_SHORT).show()
            else            showAddSheet(null)
        }
    }

    private fun observeHosts() {
        lifecycleScope.launch {
            vm.filteredHosts.collectLatest { hosts -> adapter.submitList(hosts) }
        }
        lifecycleScope.launch {
            vm.networkState.collectLatest { state -> adapter.setNetworkState(state) }
        }
    }

    private fun showAddSheet(host: com.multiping.data.Host?) {
        val sheet = AddHostBottomSheet.newInstance(host, vm)
        sheet.onSave = { newHost ->
            if (host == null) vm.addHost(newHost) else vm.updateHost(newHost)
        }
        sheet.show(supportFragmentManager, "add_host")
    }

    private fun applyLanguage() {
        val lang   = vm.prefs.getString(PingService.KEY_LANGUAGE, "ru") ?: "ru"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        // RTL поддержка для арабского
        if (lang == "ar") {
            config.setLayoutDirection(locale)
        } else {
            config.setLayoutDirection(Locale.ENGLISH)
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) startPingService()
            else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPingService()
        }
    }

    private fun startPingService() {
        val intent = PingService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
