package com.smartbluetoothsleeptracker.data

import com.smartbluetoothsleeptracker.data.db.DailyUsageEntity
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unit tests for session entity fields, usage date formatting, daily aggregations,
 * and connected duration streak logic.
 */
class SessionUsageMathTest {

    @Test
    fun testSessionEntity_audioStoppedAndDisconnectConfirmed() {
        val session = SessionEntity(
            id = 42L,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Test Headset",
            startTime = 1000L,
            endTime = 4000L,
            plannedDurationMin = 30,
            actualDurationMin = 50,
            audioStopped = true,
            disconnectConfirmed = true,
            extendedMinutes = 20,
            date = "2026-10-09"
        )

        assertEquals(42L, session.id)
        assertEquals("AA:BB:CC:DD:EE:FF", session.deviceAddress)
        assertEquals(30, session.plannedDurationMin)
        assertEquals(50, session.actualDurationMin)
        assertTrue(session.audioStopped)
        assertTrue(session.disconnectConfirmed)
        assertEquals(20, session.extendedMinutes)
    }

    @Test
    fun testDailyUsage_dateFormatConsistency() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val fixedTimestamp = 1700000000000L // 2023-11-14T22:13:20Z in UTC
        val formatted = sdf.format(Date(fixedTimestamp))

        assertEquals("2023-11-14", formatted)
    }

    @Test
    fun testDailyUsageAggregation() {
        val usageRecords = listOf(
            DailyUsageEntity(date = "2026-10-01", deviceAddress = "AA:BB:CC:DD:EE:01", totalMinutes = 45, sessionCount = 1),
            DailyUsageEntity(date = "2026-10-02", deviceAddress = "AA:BB:CC:DD:EE:01", totalMinutes = 60, sessionCount = 2),
            DailyUsageEntity(date = "2026-10-03", deviceAddress = "AA:BB:CC:DD:EE:01", totalMinutes = 30, sessionCount = 1)
        )

        val totalMinutes = usageRecords.sumOf { it.totalMinutes }
        val totalSessions = usageRecords.sumOf { it.sessionCount }
        val dailyAverage = totalMinutes / usageRecords.size

        assertEquals(135, totalMinutes)
        assertEquals(4, totalSessions)
        assertEquals(45, dailyAverage)
    }

    @Test
    fun testConnectedTimeStreakCalculation() {
        // Safe threshold: <= 60 minutes/day
        val dayUsage = listOf(
            45, // Safe (Day 1)
            30, // Safe (Day 2)
            55, // Safe (Day 3)
            50, // Safe (Day 4)
            120, // Exceeded (>60m, breaks streak)
            30  // Safe (Day 6)
        )

        // Calculate consecutive safe days from most recent (reverse)
        var streak = 0
        for (mins in dayUsage.reversed()) {
            if (mins <= 60) {
                streak++
            } else {
                break
            }
        }

        assertEquals(1, streak) // Only Day 6 before the break
    }

    @Test
    fun testConnectedTimeFullSafeStreak() {
        val dayUsage = listOf(30, 45, 50, 20, 15, 60, 40)
        var streak = 0
        for (mins in dayUsage.reversed()) {
            if (mins <= 60) {
                streak++
            } else {
                break
            }
        }
        assertEquals(7, streak)
    }
}
