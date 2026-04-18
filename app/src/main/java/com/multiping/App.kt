package com.multiping

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import com.multiping.notification.NotificationHelper

class App : Application() {

    private lateinit var screenReceiver: ScreenReceiver

    override fun onCreate() {
        super.onCreate()

        // Форсируем тёмную тему — всегда, независимо от системы
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        NotificationHelper.createChannels(this)

        // ScreenReceiver живёт на уровне Application
        screenReceiver = ScreenReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }
}
