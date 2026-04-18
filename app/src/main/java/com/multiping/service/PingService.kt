package com.multiping.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.multiping.data.AppDatabase
import com.multiping.data.Host
import com.multiping.data.HostRepository
import com.multiping.data.NetworkState
import com.multiping.data.PingStats
import com.multiping.network.CheckExecutor
import com.multiping.network.NetworkMonitor
import com.multiping.notification.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import java.util.LinkedList

class PingService : Service() {

    companion object {
        const val ACTION_START = "com.multiping.START"
        const val ACTION_STOP  = "com.multiping.STOP"
        const val TAG          = "PingService"

        const val PREFS_NAME                  = "multiping_prefs"
        const val KEY_GREEN_THRESHOLD         = "green_threshold"
        const val KEY_RED_THRESHOLD           = "red_threshold"
        const val KEY_SHOW_NOTIFICATION       = "show_notification"
        const val KEY_LANGUAGE                = "app_language"
        const val KEY_PAUSE_ON_SCREEN_OFF     = "pause_on_screen_off"
        const val KEY_MONITOR_FAVS_SCREEN_OFF = "monitor_favs_screen_off"
        const val KEY_SHOW_ON_LOCK_SCREEN     = "show_on_lock_screen"

        const val ADAPTIVE_MULTIPLIER = 1.5
        const val ADAPTIVE_MAX_MS     = 300_000L

        fun startIntent(context: Context) =
            Intent(context, PingService::class.java).apply { action = ACTION_START }
        fun stopIntent(context: Context) =
            Intent(context, PingService::class.java).apply { action = ACTION_STOP }
    }

    private val serviceScope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository:     HostRepository
    private lateinit var prefs:          SharedPreferences
    private lateinit var networkMonitor: NetworkMonitor
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var currentNetworkState: NetworkState = NetworkState.CONNECTED

    private val pingJobs          = mutableMapOf<Long, Job>()
    private val jobsMutex         = Mutex()
    private val pingHistory       = mutableMapOf<Long, LinkedList<Pair<Long, Long>>>()
    private val historyMutex      = Mutex()
    private val adaptiveIntervals = mutableMapOf<Long, Long>()

    override fun onCreate() {
        super.onCreate()
        val db          = AppDatabase.getInstance(applicationContext)
        repository      = HostRepository(db.hostDao())
        prefs           = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        networkMonitor  = NetworkMonitor(applicationContext)
        NotificationHelper.createChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock(); stopSelf(); return START_NOT_STICKY
        }

        acquireWakeLock()

        startForeground(
            NotificationHelper.NOTIFICATION_ID_MONITOR,
            NotificationHelper.buildMonitorNotification(
                applicationContext, PingStats(0, 0),
                networkMonitor.currentState(),
                greenThreshold(), redThreshold(), showNotification(), showOnLockScreen()
            )
        )

        observeNetwork()
        startMonitoring()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved — scheduling restart")
        val restartIntent = Intent(applicationContext, PingService::class.java).apply {
            action = ACTION_START
            setPackage(packageName)
        }
        val pi = android.app.PendingIntent.getService(
            applicationContext, 1, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(android.app.AlarmManager::class.java)
        am.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, pi)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "MultiPing::PingWakeLock"
        ).apply { acquire(24 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun observeNetwork() {
        serviceScope.launch {
            networkMonitor.networkState.collectLatest { state ->
                currentNetworkState = state
                if (state != NetworkState.CONNECTED) pauseAllJobs()
                else resumeJobs(repository.getEnabledHosts())
                refreshNotification()
            }
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            repository.allHosts.collectLatest { hosts ->
                syncJobs(hosts)
                refreshNotification()
            }
        }
    }

    private suspend fun syncJobs(hosts: List<Host>) {
        jobsMutex.lock()
        try {
            val activeIds  = hosts.filter { it.isEnabled }.map { it.id }.toSet()
            val runningIds = pingJobs.keys.toSet()
            (runningIds - activeIds).forEach { id ->
                pingJobs[id]?.cancel()
                pingJobs.remove(id)
                adaptiveIntervals.remove(id)
            }
            if (currentNetworkState == NetworkState.CONNECTED) {
                hosts.filter { it.isEnabled && it.id !in runningIds }.forEach { h ->
                    pingJobs[h.id] = launchPingJob(h)
                }
            }
        } finally { jobsMutex.unlock() }
    }

    private suspend fun pauseAllJobs() {
        jobsMutex.lock()
        try { pingJobs.values.forEach { it.cancel() }; pingJobs.clear() }
        finally { jobsMutex.unlock() }
    }

    private suspend fun resumeJobs(hosts: List<Host>) {
        jobsMutex.lock()
        try {
            hosts.filter { it.id !in pingJobs }.forEach { h ->
                pingJobs[h.id] = launchPingJob(h)
            }
        } finally { jobsMutex.unlock() }
    }

    private fun launchPingJob(host: Host): Job = serviceScope.launch {
        var currentInterval = adaptiveIntervals[host.id] ?: host.intervalMs
        while (isActive) {
            val start   = System.currentTimeMillis()
            val online  = CheckExecutor.check(host)
            val elapsed = System.currentTimeMillis() - start
            val avg     = updateAvg(host, elapsed, online)

            repository.updateStatus(host.id, online, if (online) elapsed else -1L, avg)
            handleFavouriteAlert(host, online)
            refreshNotification()

            currentInterval = if (!host.isFavourite) {
                if (!online) minOf((currentInterval * ADAPTIVE_MULTIPLIER).toLong(), ADAPTIVE_MAX_MS)
                else         host.intervalMs
            } else {
                host.intervalMs
            }
            adaptiveIntervals[host.id] = currentInterval
            delay(currentInterval)
        }
    }

    private suspend fun updateAvg(host: Host, pingMs: Long, online: Boolean): Long {
        if (host.avgWindowSec <= 0) return -1L
        val now = System.currentTimeMillis(); val windowMs = host.avgWindowSec * 1000L
        historyMutex.lock()
        return try {
            val q = pingHistory.getOrPut(host.id) { LinkedList() }
            if (online && pingMs > 0) q.add(Pair(now, pingMs))
            while (q.isNotEmpty() && now - q.peek().first > windowMs) q.poll()
            if (q.isEmpty()) -1L else q.map { it.second }.average().toLong()
        } finally { historyMutex.unlock() }
    }

    private suspend fun handleFavouriteAlert(host: Host, isOnline: Boolean) {
        if (!host.isFavourite) return
        val current = repository.getAllHosts().firstOrNull { it.id == host.id } ?: return
        when {
            !isOnline && !current.alertSent -> {
                NotificationHelper.sendFavouriteAlert(applicationContext, current)
                repository.setAlertSent(host.id, true)
            }
            isOnline && current.alertSent -> {
                NotificationHelper.cancelFavouriteAlert(applicationContext, host.id)
                repository.setAlertSent(host.id, false)
            }
        }
    }

    private suspend fun refreshNotification() {
        val all     = repository.getAllHosts()
        val enabled = all.filter { it.isEnabled }
        val stats   = PingStats(enabled.size, enabled.count { it.lastStatus })
        NotificationHelper.updateMonitor(
            applicationContext, stats, currentNetworkState,
            greenThreshold(), redThreshold(), showNotification(), showOnLockScreen()
        )
    }

    private fun greenThreshold()  = prefs.getInt(KEY_GREEN_THRESHOLD, 90)
    private fun redThreshold()    = prefs.getInt(KEY_RED_THRESHOLD, 10)
    private fun showNotification() = prefs.getBoolean(KEY_SHOW_NOTIFICATION, true)
    private fun showOnLockScreen() = prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, true)

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
