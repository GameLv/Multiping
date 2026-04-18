package com.multiping.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Управляет WorkManager задачами для фонового мониторинга.
 *
 * Два независимых периодических задания:
 *   TAG_ALL        — все хосты, интервал 15 мин (мин. лимит WorkManager)
 *   TAG_FAVOURITES — только избранные, интервал 15 мин
 *
 * Задания запускаются только когда есть сеть (NetworkType.CONNECTED).
 */
object WorkManagerHelper {

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Запустить мониторинг всех хостов в фоне */
    fun scheduleAll(context: Context) {
        val data = Data.Builder()
            .putBoolean(PingWorker.KEY_ONLY_FAVS, false)
            .build()

        val request = PeriodicWorkRequestBuilder<PingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(data)
            .addTag(PingWorker.TAG_ALL)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PingWorker.TAG_ALL,
            ExistingPeriodicWorkPolicy.KEEP,  // не перезапускаем если уже есть
            request
        )
    }

    /** Запустить мониторинг только избранных хостов в фоне */
    fun scheduleFavourites(context: Context) {
        val data = Data.Builder()
            .putBoolean(PingWorker.KEY_ONLY_FAVS, true)
            .build()

        val request = PeriodicWorkRequestBuilder<PingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(data)
            .addTag(PingWorker.TAG_FAVOURITES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PingWorker.TAG_FAVOURITES,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Остановить мониторинг всех хостов */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PingWorker.TAG_ALL)
    }

    /** Остановить мониторинг избранных */
    fun cancelFavourites(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PingWorker.TAG_FAVOURITES)
    }

    /** Остановить всё */
    fun cancelEverything(context: Context) {
        cancelAll(context)
        cancelFavourites(context)
    }
}
