package com.multiping

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multiping.data.AppDatabase
import com.multiping.data.Host
import com.multiping.data.HostRepository
import com.multiping.data.CheckMode
import com.multiping.data.NetworkState
import com.multiping.data.PingStats
import com.multiping.network.NetworkMonitor
import com.multiping.notification.NotificationHelper
import com.multiping.service.PingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = HostRepository(AppDatabase.getInstance(app).hostDao())
    private val networkMonitor = NetworkMonitor(app)
    val prefs: SharedPreferences =
        app.getSharedPreferences(PingService.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    val hosts: StateFlow<List<Host>> = repository.allHosts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredHosts: StateFlow<List<Host>> = combine(hosts, searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { h ->
            h.address.contains(query, ignoreCase = true) ||
            h.label.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState
        .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.currentState())

    val stats: StateFlow<PingStats> = hosts.map { list ->
        val enabled = list.filter { it.isEnabled }
        PingStats(enabled.size, enabled.count { it.lastStatus })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PingStats(0, 0))

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun addHost(host: Host) = viewModelScope.launch {
        require(hosts.value.size < 256) { "Лимит 256 хостов" }
        repository.addHost(host)
    }

    fun updateHost(host: Host) = viewModelScope.launch { repository.updateHost(host) }
    fun deleteHost(host: Host) = viewModelScope.launch { repository.deleteHost(host) }

    fun setEnabled(host: Host, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(host.id, enabled)
    }

    fun toggleFavourite(host: Host) = viewModelScope.launch {
        val newFav = !host.isFavourite
        repository.setFavourite(host.id, newFav)
        // Если сняли избранное — отменяем висящее уведомление об этом хосте
        if (!newFav) {
            NotificationHelper.cancelFavouriteAlert(getApplication(), host.id)
        }
    }

    // Настройки светофора
    fun greenThreshold(): Int = prefs.getInt(PingService.KEY_GREEN_THRESHOLD, 90)
    fun redThreshold(): Int   = prefs.getInt(PingService.KEY_RED_THRESHOLD, 10)
    fun saveThresholds(green: Int, red: Int) {
        prefs.edit()
            .putInt(PingService.KEY_GREEN_THRESHOLD, green)
            .putInt(PingService.KEY_RED_THRESHOLD, red)
            .apply()
    }

    // Дефолтные значения формы
    fun defaultInterval(): Int   = prefs.getInt(KEY_DEF_INTERVAL, 5)
    fun defaultTimeout(): Int    = prefs.getInt(KEY_DEF_TIMEOUT, 3)
    fun defaultAvgWindow(): Int  = prefs.getInt(KEY_DEF_AVG_WINDOW, 60)
    fun defaultCheckMode(): CheckMode =
        CheckMode.valueOf(prefs.getString(KEY_DEF_CHECK_MODE, CheckMode.PING.name) ?: CheckMode.PING.name)

    fun saveDefaults(interval: Int, timeout: Int, avgWindow: Int, checkMode: CheckMode) {
        prefs.edit()
            .putInt(KEY_DEF_INTERVAL, interval)
            .putInt(KEY_DEF_TIMEOUT, timeout)
            .putInt(KEY_DEF_AVG_WINDOW, avgWindow)
            .putString(KEY_DEF_CHECK_MODE, checkMode.name)
            .apply()
    }

    val atLimit: Boolean get() = hosts.value.size >= 256

    companion object {
        const val KEY_DEF_INTERVAL    = "def_interval"
        const val KEY_DEF_TIMEOUT     = "def_timeout"
        const val KEY_DEF_AVG_WINDOW  = "def_avg_window"
        const val KEY_DEF_CHECK_MODE  = "def_check_mode"
    }
}
