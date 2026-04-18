package com.multiping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.multiping.service.ForegroundFavsService
import com.multiping.service.PingService
import com.multiping.worker.WorkManagerHelper

/**
 * Зарегистрирован в App.onCreate() — живёт всё время жизни приложения,
 * независимо от состояния сервисов. Это гарантирует что SCREEN_ON
 * всегда будет получен и сервис перезапустится.
 */
class ScreenReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ScreenReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PingService.PREFS_NAME, Context.MODE_PRIVATE)
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff(context, prefs)
            Intent.ACTION_SCREEN_ON  -> onScreenOn(context, prefs)
        }
    }

    private fun onScreenOff(context: Context, prefs: SharedPreferences) {
        Log.d(TAG, "Screen OFF")
        val pauseOnScreenOff = prefs.getBoolean(PingService.KEY_PAUSE_ON_SCREEN_OFF, false)
        val monitorFavs      = prefs.getBoolean(PingService.KEY_MONITOR_FAVS_SCREEN_OFF, false)

        // Останавливаем основной сервис
        context.stopService(PingService.stopIntent(context))
        WorkManagerHelper.cancelAll(context)
        context.stopService(ForegroundFavsService.stopIntent(context))

        when {
            !pauseOnScreenOff && !monitorFavs -> {
                Log.d(TAG, "WorkManager for ALL")
                WorkManagerHelper.scheduleAll(context)
            }
            !pauseOnScreenOff && monitorFavs -> {
                Log.d(TAG, "WorkManager + ForegroundFavs")
                WorkManagerHelper.scheduleAll(context)
                startForegroundFavs(context)
            }
            pauseOnScreenOff && !monitorFavs -> {
                Log.d(TAG, "Full pause")
                // Ничего не запускаем
            }
            pauseOnScreenOff && monitorFavs -> {
                Log.d(TAG, "ForegroundFavs only")
                startForegroundFavs(context)
            }
        }
    }

    private fun onScreenOn(context: Context, prefs: SharedPreferences) {
        Log.d(TAG, "Screen ON")

        // Останавливаем фоновые режимы
        WorkManagerHelper.cancelAll(context)
        context.stopService(ForegroundFavsService.stopIntent(context))

        // Проверяем — пользователь мог вручную нажать "Стоп" до блокировки.
        // Перезапускаем только если сервис должен быть активен.
        // Простое правило: при включении экрана всегда перезапускаем основной сервис.
        // Если пользователь нажал стоп — он может снова запустить кнопкой в UI.
        Handler(Looper.getMainLooper()).postDelayed({
            tryStartMainService(context)
        }, 600)
    }

    private fun tryStartMainService(context: Context) {
        val intent = PingService.startIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
            Log.d(TAG, "Main service started on SCREEN_ON")
        } catch (e: Exception) {
            Log.e(TAG, "Start failed: ${e.message}, retrying in 2s")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(intent)
                    else
                        context.startService(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Retry failed: ${e2.message}")
                }
            }, 2000)
        }
    }

    private fun startForegroundFavs(context: Context) {
        val intent = ForegroundFavsService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent)
        else
            context.startService(intent)
    }
}
