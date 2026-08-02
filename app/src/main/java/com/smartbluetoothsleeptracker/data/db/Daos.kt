package com.smartbluetoothsleeptracker.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY last_connected_at DESC")
    fun allDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE is_favorite = 1")
    fun favoriteDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE is_favorite = 1")
    suspend fun getFavoriteDevicesNow(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun getDevice(address: String): DeviceEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("UPDATE devices SET is_favorite = :fav WHERE address = :address")
    suspend fun setFavorite(address: String, fav: Boolean)

    @Query("UPDATE devices SET device_type = :type WHERE address = :address")
    suspend fun setDeviceType(address: String, type: DeviceType)

    @Query("UPDATE devices SET working_disconnect_method = :method WHERE address = :address")
    suspend fun setWorkingMethod(address: String, method: String?)

    @Query("UPDATE devices SET last_connected_at = :time WHERE address = :address")
    suspend fun updateLastConnected(address: String, time: Long)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE address = :address")
    suspend fun deleteByAddress(address: String)
}

@Dao
interface DisconnectAttemptDao {
    @Query("SELECT * FROM disconnect_attempts WHERE device_address = :address ORDER BY last_tested_at DESC")
    fun attemptsForDevice(address: String): Flow<List<DisconnectAttemptEntity>>

    @Query("SELECT * FROM disconnect_attempts WHERE device_address = :address ORDER BY last_tested_at DESC")
    suspend fun getAttemptsForDeviceNow(address: String): List<DisconnectAttemptEntity>

    @Insert
    suspend fun insert(attempt: DisconnectAttemptEntity)

    @Query("DELETE FROM disconnect_attempts WHERE device_address = :address")
    suspend fun clearForDevice(address: String)

    @Query("DELETE FROM disconnect_attempts WHERE device_address = :address AND id NOT IN (SELECT id FROM disconnect_attempts WHERE device_address = :address ORDER BY last_tested_at DESC LIMIT :keep)")
    suspend fun deleteOldAttempts(address: String, keep: Int)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun allSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date = :date ORDER BY start_time DESC")
    fun sessionsForDate(date: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date BETWEEN :from AND :to ORDER BY start_time DESC")
    fun sessionsInRange(from: String, to: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date BETWEEN :from AND :to ORDER BY start_time DESC")
    suspend fun sessionsInRangeNow(from: String, to: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE end_time IS NULL ORDER BY start_time DESC LIMIT 1")
    suspend fun getOrphanedSession(): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions WHERE device_address = :address")
    suspend fun deleteForDevice(address: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sessions")
    fun totalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sessions WHERE date BETWEEN :from AND :to")
    suspend fun countInRange(from: String, to: String): Int
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getForDate(date: String): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun usageInRange(from: String, to: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun usageInRangeNow(from: String, to: String): List<DailyUsageEntity>

    @Query("""
        SELECT * FROM daily_usage 
        WHERE date BETWEEN :from AND :to 
        AND device_address IN (SELECT address FROM devices WHERE device_type IN ('EARBUDS','NECKBAND'))
        ORDER BY date ASC
    """)
    suspend fun earHealthUsageInRange(from: String, to: String): List<DailyUsageEntity>

    @Upsert
    suspend fun upsert(usage: DailyUsageEntity)

    @Query("DELETE FROM daily_usage WHERE device_address = :address")
    suspend fun deleteForDevice(address: String)

    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}
