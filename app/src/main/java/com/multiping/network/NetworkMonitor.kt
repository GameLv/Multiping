package com.multiping.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.multiping.data.NetworkState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.HttpURLConnection
import java.net.URL

class NetworkMonitor(private val context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    /**
     * Flow эмитит NetworkState при каждом изменении состояния сети.
     */
    val networkState: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network)  { trySend(resolveState()) }
            override fun onLost(network: Network)       { trySend(resolveState()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(resolveState())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)
        trySend(resolveState()) // текущее состояние сразу

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentState(): NetworkState = resolveState()

    /**
     * 1. Нет активной сети                        -> OFFLINE
     * 2. Сеть есть, NET_CAPABILITY_VALIDATED нет  -> NO_INTERNET (captive portal)
     * 3. Сеть есть и VALIDATED                    -> CONNECTED
     */
    private fun resolveState(): NetworkState {
        val network = cm.activeNetwork ?: return NetworkState.OFFLINE
        val caps    = cm.getNetworkCapabilities(network) ?: return NetworkState.OFFLINE

        val hasTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        if (!hasTransport) return NetworkState.OFFLINE

        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            NetworkState.CONNECTED
        else
            NetworkState.NO_INTERNET
    }

    /**
     * Активная проверка: HEAD-запрос к Google Connectivity Check.
     * Возвращает 204 — интернет есть, иначе нет.
     */
    suspend fun hasRealInternet(): Boolean = try {
        val conn = URL("https://connectivitycheck.gstatic.com/generate_204")
            .openConnection() as HttpURLConnection
        conn.requestMethod  = "HEAD"
        conn.connectTimeout = 3000
        conn.readTimeout    = 3000
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        code == 204
    } catch (e: Exception) {
        Log.w("NetworkMonitor", "Real internet check failed: ${e.message}")
        false
    }
}
