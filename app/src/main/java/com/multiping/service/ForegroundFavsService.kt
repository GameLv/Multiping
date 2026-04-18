package com.multiping.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.multiping.R
import com.multiping.data.AppDatabase
import com.multiping.data.HostRepository
import com.multiping.network.CheckExecutor
import com.multiping.notification.NotificationHelper
import kotlinx.coroutines.*

class ForegroundFavsService : Service() {

    companion object {
        const val ACTION_START         = "com.multiping.FAVS_START"
        const val ACTION_STOP          = "com.multiping.FAVS_STOP"
        const val NOTIFICATION_ID_FAVS = 1002

        fun startIntent(context: Context) =
            Intent(context, ForegroundFavsService::class.java).apply { action = ACTION_START }
        fun stopIntent(context: Context) =
            Intent(context, ForegroundFavsService::class.java).apply { action = ACTION_STOP }
    }

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private lateinit var repository: HostRepository

    override fun onCreate() {
        super.onCreate()
        repository = HostRepository(AppDatabase.getInstance(applicationContext).hostDao())
        NotificationHelper.createChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        startForeground(
            NOTIFICATION_ID_FAVS,
            NotificationCompat.Builder(this, NotificationHelper.CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MultiPing")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setSilent(true)
                .build()
        )

        monitorFavourites()
        return START_STICKY
    }

    private fun monitorFavourites() {
        serviceScope.launch {
            val hosts = repository.getFavouriteHosts()
            if (hosts.isEmpty()) { stopSelf(); return@launch }

            hosts.forEach { host ->
                launch {
                    while (isActive) {
                        val start   = System.currentTimeMillis()
                        val online  = CheckExecutor.check(host)
                        val elapsed = System.currentTimeMillis() - start

                        repository.updateStatus(host.id, online, if (online) elapsed else -1L, -1L)

                        val current = repository.getAllHosts().firstOrNull { it.id == host.id }
                        if (current != null) {
                            when {
                                !online && !current.alertSent -> {
                                    NotificationHelper.sendFavouriteAlert(applicationContext, current)
                                    repository.setAlertSent(host.id, true)
                                }
                                online && current.alertSent -> {
                                    NotificationHelper.cancelFavouriteAlert(applicationContext, host.id)
                                    repository.setAlertSent(host.id, false)
                                }
                            }
                        }
                        delay(host.intervalMs)
                    }
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); serviceJob.cancel() }
    override fun onBind(intent: Intent?): IBinder? = null
}
