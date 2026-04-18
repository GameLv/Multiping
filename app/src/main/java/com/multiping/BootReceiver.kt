package com.multiping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.multiping.service.PingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // После ребута всегда запускаем основной сервис —
        // ScreenReceiver подхватит переключение когда экран выключится
        val serviceIntent = PingService.startIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
