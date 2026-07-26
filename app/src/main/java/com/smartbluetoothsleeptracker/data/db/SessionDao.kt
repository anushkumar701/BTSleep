package com.smartbluetoothsleeptracker.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT 200")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date = :date ORDER BY startTime DESC")
    fun getSessionsForDate(date: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date >= :fromDate ORDER BY startTime DESC")
    fun getSessionsSince(fromDate: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date >= :fromDate AND date <= :toDate ORDER BY startTime DESC")
    fun getSessionsBetween(fromDate: String, toDate: String): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM sessions WHERE deviceName = :deviceName")
    suspend fun deleteByDeviceName(deviceName: String)

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Query("SELECT SUM(duration) FROM sessions WHERE date = :date")
    suspend fun getTotalDurationForDate(date: String): Long?

    @Query("SELECT SUM(duration) FROM sessions WHERE date >= :fromDate")
    suspend fun getTotalDurationSince(fromDate: String): Long?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getTotalCount(): Int
}
