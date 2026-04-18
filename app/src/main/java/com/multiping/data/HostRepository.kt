package com.multiping.data

import kotlinx.coroutines.flow.Flow

class HostRepository(private val dao: HostDao) {

    val allHosts: Flow<List<Host>> = dao.getAllFlow()

    suspend fun addHost(host: Host): Long = dao.insert(host)
    suspend fun updateHost(host: Host)    = dao.update(host)
    suspend fun deleteHost(host: Host)    = dao.delete(host)
    suspend fun deleteById(id: Long)      = dao.deleteById(id)

    suspend fun getEnabledHosts(): List<Host>   = dao.getEnabledHosts()
    suspend fun getAllHosts(): List<Host>        = dao.getAllHosts()
    suspend fun getFavouriteHosts(): List<Host> = dao.getFavouriteHosts()

    suspend fun updateStatus(id: Long, status: Boolean, pingMs: Long, avgPingMs: Long) =
        dao.updateStatus(id, status, pingMs, avgPingMs, System.currentTimeMillis())

    suspend fun setEnabled(id: Long, enabled: Boolean)  = dao.setEnabled(id, enabled)

    /**
     * При снятии избранного — сразу сбрасываем alertSent и отменяем уведомление.
     * При добавлении — просто ставим флаг, alertSent не трогаем (он и так false).
     */
    suspend fun setFavourite(id: Long, fav: Boolean) {
        dao.setFavourite(id, fav)
        if (!fav) {
            // Сняли избранное — сбрасываем флаг "уведомление отправлено"
            dao.setAlertSent(id, false)
        }
    }

    suspend fun setAlertSent(id: Long, sent: Boolean) = dao.setAlertSent(id, sent)

    suspend fun countEnabled(): Int = dao.countEnabled()
}
