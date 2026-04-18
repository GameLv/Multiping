package com.multiping.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Режим проверки хоста */
enum class CheckMode { PING, HTTP, TCP }

@Entity(tableName = "hosts")
data class Host(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val address: String,
    val label: String = "",

    /** Режим проверки: PING / HTTP / TCP */
    val checkMode: String = CheckMode.PING.name,

    // HTTP-специфика
    val httpMethod: String = "GET",

    // TCP-специфика
    val tcpPort: Int = 80,

    val intervalMs: Long = 5000L,
    val timeoutMs: Int = 3000,

    // Окно усреднения в секундах (0 = не показывать среднее)
    val avgWindowSec: Int = 60,

    val isEnabled: Boolean = true,
    val isFavourite: Boolean = false,

    // Результаты (обновляются сервисом)
    val lastStatus: Boolean = false,
    val lastPingMs: Long = -1L,
    val avgPingMs: Long = -1L,
    val lastCheckedAt: Long = 0L,

    val alertSent: Boolean = false
)

/** Обратная совместимость: старые записи имели useCurl=0/1 (мигрируем в checkMode) */
val Host.resolvedCheckMode: CheckMode get() = CheckMode.valueOf(checkMode)

data class PingStats(
    val total: Int,
    val online: Int
) {
    val offline: Int get() = total - online
    val ratio: Float get() = if (total == 0) 0f else online.toFloat() / total.toFloat()

    fun trafficLight(greenThreshold: Int, redThreshold: Int): TrafficLight {
        val pct = (ratio * 100).toInt()
        return when {
            pct >= greenThreshold -> TrafficLight.GREEN
            pct <= redThreshold   -> TrafficLight.RED
            else                  -> TrafficLight.YELLOW
        }
    }
}

enum class TrafficLight { GREEN, YELLOW, RED }

enum class NetworkState {
    CONNECTED,
    NO_INTERNET,
    OFFLINE
}
