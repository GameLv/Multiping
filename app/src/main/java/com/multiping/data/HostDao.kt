package com.multiping.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {

    @Query("SELECT * FROM hosts ORDER BY isFavourite DESC, id ASC")
    fun getAllFlow(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE isEnabled = 1 ORDER BY isFavourite DESC, id ASC")
    suspend fun getEnabledHosts(): List<Host>

    @Query("SELECT * FROM hosts ORDER BY isFavourite DESC, id ASC")
    suspend fun getAllHosts(): List<Host>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(host: Host): Long

    @Update
    suspend fun update(host: Host)

    @Delete
    suspend fun delete(host: Host)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        UPDATE hosts
        SET lastStatus = :status,
            lastPingMs = :pingMs,
            avgPingMs  = :avgPingMs,
            lastCheckedAt = :ts
        WHERE id = :id
    """)
    suspend fun updateStatus(id: Long, status: Boolean, pingMs: Long, avgPingMs: Long, ts: Long)

    @Query("UPDATE hosts SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE hosts SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: Long, fav: Boolean)

    @Query("UPDATE hosts SET alertSent = :sent WHERE id = :id")
    suspend fun setAlertSent(id: Long, sent: Boolean)

    @Query("SELECT * FROM hosts WHERE isFavourite = 1 AND isEnabled = 1")
    suspend fun getFavouriteHosts(): List<Host>

    @Query("SELECT COUNT(*) FROM hosts WHERE isEnabled = 1")
    suspend fun countEnabled(): Int
}
