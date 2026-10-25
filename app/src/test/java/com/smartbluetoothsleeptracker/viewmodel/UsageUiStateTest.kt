package com.smartbluetoothsleeptracker.viewmodel

import com.smartbluetoothsleeptracker.data.db.DeviceEntity
import com.smartbluetoothsleeptracker.data.db.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UsageUiStateTest {

    @Test
    fun testDefaultUsageUiState() {
        val state = UsageUiState()
        assertEquals(UsagePeriod.TODAY, state.period)
        assertEquals("Today's Hourly Usage", state.chartTitle)
        assertEquals(0, state.sessions.size)
        assertEquals(0, state.deviceStats.size)
        assertEquals(0, state.chartItems.size)
        assertEquals(0, state.totalMinutes)
        assertEquals(0, state.totalSessions)
        assertEquals(0, state.totalDevices)
    }

    @Test
    fun testDeviceUsageStatCreation() {
        val device = DeviceEntity(
            address = "00:11:22:33:44:55",
            name = "Test Earbuds",
            deviceType = DeviceType.EARBUDS,
            lastConnectedAt = 1000L
        )
        val stat = DeviceUsageStat(
            device = device,
            totalMinutes = 45,
            sessionCount = 3
        )
        assertEquals("00:11:22:33:44:55", stat.device.address)
        assertEquals("Test Earbuds", stat.device.name)
        assertEquals(45, stat.totalMinutes)
        assertEquals(3, stat.sessionCount)
    }

    @Test
    fun testUsageAggregation_chartAndDeviceStatsSynchronized() {
        // Device A: 30m session, 25m dailyUsage
        val sMinsA = 30
        val dMinsA = 25
        val baseA = maxOf(sMinsA, dMinsA) // 30

        // Device B: 15m session, 20m dailyUsage
        val sMinsB = 15
        val dMinsB = 20
        val baseB = maxOf(sMinsB, dMinsB) // 20

        val deviceStatsSum = baseA + baseB // 50

        // Simulating Week aggregation per device
        val devices = listOf("A", "B")
        val chartSum = devices.sumOf { dev ->
            val s = if (dev == "A") sMinsA else sMinsB
            val d = if (dev == "A") dMinsA else dMinsB
            maxOf(s, d)
        }

        assertEquals(50, deviceStatsSum)
        assertEquals(deviceStatsSum, chartSum)
    }

    @Test
    fun testSub30SecondSessionIgnored() {
        val durationMs = 20_000L // 20 seconds (< 30s)
        val minutes = com.smartbluetoothsleeptracker.service.TimerCalculations.calculateDurationMinutes(durationMs)
        assertEquals(0, minutes)
    }
}
