package com.smartbluetoothsleeptracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * In-memory Room database tests for atomic usage recording and session entity operations.
 */
@RunWith(RobolectricTestRunner::class)
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var deviceDao: DeviceDao
    private lateinit var dailyUsageDao: DailyUsageDao
    private lateinit var sessionDao: SessionDao

    @Before
    fun createDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceDao = db.deviceDao()
        dailyUsageDao = db.dailyUsageDao()
        sessionDao = db.sessionDao()

        // Insert parent device to satisfy foreign key constraints
        deviceDao.upsert(
            DeviceEntity(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Pixel Buds",
                deviceType = DeviceType.EARBUDS
            )
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRecordUsage_atomicInsertAndUpdate() = runBlocking {
        val date = "2026-10-09"
        val addr = "AA:BB:CC:DD:EE:FF"

        // First session: 30 minutes
        dailyUsageDao.recordUsage(date, addr, 30)

        var usageList = dailyUsageDao.getForDate(date)
        assertEquals(1, usageList.size)
        assertEquals(30, usageList[0].totalMinutes)
        assertEquals(1, usageList[0].sessionCount)

        // Second session same day and device: 15 minutes
        dailyUsageDao.recordUsage(date, addr, 15)

        usageList = dailyUsageDao.getForDate(date)
        assertEquals(1, usageList.size)
        assertEquals(45, usageList[0].totalMinutes)
        assertEquals(2, usageList[0].sessionCount)
    }

    @Test
    fun testSessionEntity_upsertWithAudioStoppedAndDisconnectConfirmed() = runBlocking {
        val session = SessionEntity(
            id = 0L,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Pixel Buds",
            startTime = 1000L,
            endTime = 2000L,
            plannedDurationMin = 30,
            actualDurationMin = 25,
            audioStopped = true,
            disconnectConfirmed = true,
            extendedMinutes = 5,
            date = "2026-10-09"
        )
        val id = sessionDao.upsert(session)
        val loaded = sessionDao.getById(id)

        assertEquals(true, loaded?.audioStopped)
        assertEquals(true, loaded?.disconnectConfirmed)
        assertEquals(5, loaded?.extendedMinutes)
    }
}
