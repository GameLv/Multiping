package com.multiping.network

import com.multiping.data.CheckMode
import com.multiping.data.Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Единое место для всех трёх типов проверки хостов.
 * Используется в PingService, ForegroundFavsService и PingWorker.
 */
object CheckExecutor {

    suspend fun check(host: Host): Boolean = withContext(Dispatchers.IO) {
        when (CheckMode.valueOf(host.checkMode)) {
            CheckMode.PING -> doPing(host)
            CheckMode.HTTP -> doHttp(host)
            CheckMode.TCP  -> doTcp(host)
        }
    }

    private fun doPing(host: Host): Boolean = try {
        InetAddress.getByName(host.address).isReachable(host.timeoutMs)
    } catch (e: Exception) { false }

    private fun doHttp(host: Host): Boolean = try {
        val url  = if (host.address.startsWith("http")) host.address else "https://${host.address}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod           = host.httpMethod
        conn.connectTimeout          = host.timeoutMs
        conn.readTimeout             = host.timeoutMs
        conn.instanceFollowRedirects = true
        conn.connect()
        val code = conn.responseCode
        conn.disconnect()
        code in 200..499
    } catch (e: Exception) { false }

    private fun doTcp(host: Host): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host.address, host.tcpPort), host.timeoutMs)
            socket.isConnected
        }
    } catch (e: Exception) { false }
}
