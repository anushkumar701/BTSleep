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
        assertEquals(UsagePeriod.WEEK, state.period)
        assertEquals(0, state.sessions.size)
        assertEquals(0, state.deviceStats.size)
        assertEquals(0, state.dailyUsage.size)
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
}
