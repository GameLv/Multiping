package com.multiping.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import java.util.Locale
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.multiping.MainActivity
import com.multiping.R
import com.multiping.SettingsActivity
import com.multiping.data.Host
import com.multiping.data.NetworkState
import com.multiping.data.PingStats
import com.multiping.data.TrafficLight

object NotificationHelper {

    const val CHANNEL_MONITOR = "multiping_service"
    // Базовый ID канала алертов — к нему добавляется суффикс звука
    private const val CHANNEL_ALERTS_BASE = "multiping_alerts"

    const val NOTIFICATION_ID_MONITOR = 1001
    private const val NOTIFICATION_ID_ALERT_BASE = 2000

    // Ключ для хранения текущего ID канала алертов
    private const val PREF_ALERTS_CHANNEL_ID = "alerts_channel_id"

    // ── Получаем актуальный ID канала алертов ─────────────────────────────
    fun getAlertsChannelId(context: Context): String {
        val prefs = context.getSharedPreferences("multiping_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREF_ALERTS_CHANNEL_ID, CHANNEL_ALERTS_BASE)
            ?: CHANNEL_ALERTS_BASE
    }

    // ── Создание каналов при старте приложения ────────────────────────────
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        // Монитор — без звука, создаём один раз
        if (nm.getNotificationChannel(CHANNEL_MONITOR) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.notif_channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_monitor_desc)
                    setShowBadge(false)
                }
            )
        }

        // Алерты — создаём если канал с текущим ID ещё не существует
        val channelId = getAlertsChannelId(context)
        if (nm.getNotificationChannel(channelId) == null) {
            createAlertsChannelWithId(context, nm, channelId)
        }
    }

    /**
     * Вызывается при сохранении новых настроек звука.
     * Генерирует новый ID канала → создаёт канал с нужным звуком →
     * сохраняет ID. Старый канал удаляем чтобы не засорять список.
     */
    fun applyNewAlertSound(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm    = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("multiping_prefs", Context.MODE_PRIVATE)

        val soundPref = prefs.getString(SettingsActivity.KEY_ALERT_SOUND_URI, "") ?: ""

        // Уникальный ID на основе звука
        val suffix    = soundPref.hashCode().toString().replace("-", "n")
        val newId     = "${CHANNEL_ALERTS_BASE}_$suffix"
        val oldId     = getAlertsChannelId(context)

        // Создаём новый канал
        if (nm.getNotificationChannel(newId) == null) {
            createAlertsChannelWithId(context, nm, newId)
        }

        // Удаляем старый если ID изменился
        if (oldId != newId && oldId != CHANNEL_ALERTS_BASE) {
            nm.deleteNotificationChannel(oldId)
        }

        // Сохраняем новый ID
        prefs.edit().putString(PREF_ALERTS_CHANNEL_ID, newId).apply()
    }

    private fun createAlertsChannelWithId(
        context: Context,
        nm: NotificationManager,
        channelId: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prefs     = context.getSharedPreferences("multiping_prefs", Context.MODE_PRIVATE)
        val soundPref = prefs.getString(SettingsActivity.KEY_ALERT_SOUND_URI, "") ?: ""

        val soundUri: Uri? = when (soundPref) {
            ""     -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "none" -> null
            else   -> try { Uri.parse(soundPref) }
                     catch (e: Exception) {
                         RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                     }
        }

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notif_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alerts_desc)
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
            if (soundUri != null) {
                val audioAttr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, audioAttr)
            } else {
                setSound(null, null)
            }
        }
        nm.createNotificationChannel(channel)
    }


    /** Создаём контекст с языком из настроек — для корректной локализации уведомлений */
    private fun localizedContext(context: Context): android.content.Context {
        val prefs = context.getSharedPreferences("multiping_prefs", android.content.Context.MODE_PRIVATE)
        val lang  = prefs.getString(com.multiping.service.PingService.KEY_LANGUAGE, "ru") ?: "ru"
        val locale = Locale(lang)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    // ── Монитор-уведомление ───────────────────────────────────────────────
    fun buildMonitorNotification(
        context: Context,
        stats: PingStats,
        networkState: NetworkState,
        greenThreshold: Int,
        redThreshold: Int,
        showNotification: Boolean = true,
        showOnLockScreen: Boolean = true
    ): Notification {
        val openIntent = buildOpenIntent(context)

        if (!showNotification) {
            return NotificationCompat.Builder(context, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MultiPing")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                .setContentIntent(openIntent)
                .build()
        }

        return when (networkState) {
            NetworkState.OFFLINE ->
                buildOfflineNotification(context, openIntent,
                    context.getString(R.string.notif_title_offline),
                    context.getString(R.string.notif_text_offline), showOnLockScreen)
            NetworkState.NO_INTERNET ->
                buildOfflineNotification(context, openIntent,
                    context.getString(R.string.notif_title_no_internet),
                    context.getString(R.string.notif_text_no_internet), showOnLockScreen)
            NetworkState.CONNECTED ->
                buildTrafficLightNotification(context, openIntent, stats,
                    greenThreshold, redThreshold, showOnLockScreen)
        }
    }

    private fun buildTrafficLightNotification(
        context: Context, openIntent: PendingIntent,
        stats: PingStats, greenThreshold: Int, redThreshold: Int,
        showOnLockScreen: Boolean = true
    ): Notification {
        val lc = localizedContext(context)
        val light = stats.trafficLight(greenThreshold, redThreshold)
        val (emoji, statusText, color) = when (light) {
            TrafficLight.GREEN  -> Triple("🟢", lc.getString(R.string.notif_status_green),  Color.GREEN)
            TrafficLight.YELLOW -> Triple("🟡", lc.getString(R.string.notif_status_yellow), Color.YELLOW)
            TrafficLight.RED    -> Triple("🔴", lc.getString(R.string.notif_status_red),    Color.RED)
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji MultiPing · ${stats.online}/${stats.total}")
            .setContentText(statusText)
            .setColor(color).setColorized(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openIntent)
            .setVisibility(if (showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC
                           else NotificationCompat.VISIBILITY_SECRET)
            .addAction(R.drawable.ic_stop, lc.getString(R.string.notif_btn_stop),
                buildStopIntent(context))
            .build()
    }

    private fun buildOfflineNotification(
        context: Context, openIntent: PendingIntent,
        title: String, text: String, showOnLockScreen: Boolean = true
    ): Notification {
        val lc = localizedContext(context)
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text)
            .setColor(Color.GRAY).setColorized(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openIntent)
            .setVisibility(if (showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC
                           else NotificationCompat.VISIBILITY_SECRET)
            .addAction(R.drawable.ic_stop, lc.getString(R.string.notif_btn_stop),
                buildStopIntent(context))
            .build()
    }

    fun updateMonitor(
        context: Context, stats: PingStats, networkState: NetworkState,
        greenThreshold: Int, redThreshold: Int,
        showNotification: Boolean, showOnLockScreen: Boolean = true
    ) {
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID_MONITOR,
            buildMonitorNotification(context, stats, networkState,
                greenThreshold, redThreshold, showNotification, showOnLockScreen)
        )
    }

    // ── Push-алерт — использует актуальный канал с нужным звуком ──────────
    fun sendFavouriteAlert(context: Context, host: Host) {
        val nm      = context.getSystemService(NotificationManager::class.java)
        val name    = host.label.ifBlank { host.address }
        val channel = getAlertsChannelId(context)
        val lc      = localizedContext(context)

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(lc.getString(R.string.notif_alert_title, name))
            .setContentText(lc.getString(R.string.notif_alert_text, host.address))
            .setColor(Color.RED)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenIntent(context))
            .build()

        nm.notify((NOTIFICATION_ID_ALERT_BASE + host.id).toInt(), notification)
    }

    fun cancelFavouriteAlert(context: Context, hostId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel((NOTIFICATION_ID_ALERT_BASE + hostId).toInt())
    }

    private fun buildOpenIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildStopIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context, 0,
            Intent(context, com.multiping.service.PingService::class.java).apply {
                action = com.multiping.service.PingService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
