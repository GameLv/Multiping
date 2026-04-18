package com.multiping.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multiping.data.AppDatabase
import com.multiping.data.HostRepository
import com.multiping.network.CheckExecutor
import com.multiping.notification.NotificationHelper

class PingWorker(
    params: WorkerParameters,
    private val context: Context
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG_ALL        = "ping_worker_all"
        const val TAG_FAVOURITES = "ping_worker_favourites"
        const val KEY_ONLY_FAVS  = "only_favourites"
    }

    override suspend fun doWork(): Result {
        val onlyFavourites = inputData.getBoolean(KEY_ONLY_FAVS, false)
        val repo = HostRepository(AppDatabase.getInstance(context).hostDao())

        val hosts = if (onlyFavourites) repo.getFavouriteHosts() else repo.getEnabledHosts()

        hosts.forEach { host ->
            val start   = System.currentTimeMillis()
            val online  = CheckExecutor.check(host)
            val elapsed = System.currentTimeMillis() - start

            repo.updateStatus(host.id, online, if (online) elapsed else -1L, -1L)

            if (host.isFavourite) {
                val current = repo.getAllHosts().firstOrNull { it.id == host.id } ?: return@forEach
                when {
                    !online && !current.alertSent -> {
                        NotificationHelper.sendFavouriteAlert(context, current)
                        repo.setAlertSent(host.id, true)
                    }
                    online && current.alertSent -> {
                        NotificationHelper.cancelFavouriteAlert(context, host.id)
                        repo.setAlertSent(host.id, false)
                    }
                }
            }
        }
        return Result.success()
    }
}
