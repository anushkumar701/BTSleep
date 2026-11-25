package com.smartbluetoothsleeptracker.core.bluetooth

import com.smartbluetoothsleeptracker.core.bluetooth.case.CaseBattery
import com.smartbluetoothsleeptracker.data.db.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryInfoTest {

    @Test
    fun testOnlyLeftBudActive() {
        val info = BatteryInfo(
            mainLevel = 80,
            leftLevel = 80,
            rightLevel = -1,
            caseLevel = -1
        )
        val summary = info.formatSummary(DeviceType.EARBUDS)
        assertEquals("Left: 80%", summary)
        assertFalse(summary.contains("Right"))
        assertFalse(summary.contains("Battery:"))
    }

    @Test
    fun testOnlyRightBudActive() {
        val info = BatteryInfo(
            mainLevel = 85,
            leftLevel = -1,
            rightLevel = 85,
            caseLevel = -1
        )
        val summary = info.formatSummary(DeviceType.EARBUDS)
        assertEquals("Right: 85%", summary)
        assertFalse(summary.contains("Left"))
        assertFalse(summary.contains("Battery:"))
    }

    @Test
    fun testBothBudsActive() {
        val info = BatteryInfo(
            mainLevel = 85,
            leftLevel = 80,
            rightLevel = 90,
            caseLevel = -1
        )
        val summary = info.formatSummary(DeviceType.EARBUDS)
        assertEquals("Left: 80%  •  Right: 90%", summary)
    }

    @Test
    fun testBothBudsWithCase() {
        val info = BatteryInfo(
            mainLevel = 85,
            leftLevel = 80,
            rightLevel = 90,
            caseLevel = 100,
            caseBattery = CaseBattery(percent = 100, charging = false)
        )
        val summary = info.formatSummary(DeviceType.EARBUDS)
        assertEquals("Left: 80%  •  Right: 90%  •  Case: 100%", summary)
    }

    @Test
    fun testSingleLeftBudWithChargingCase() {
        val info = BatteryInfo(
            mainLevel = 80,
            leftLevel = 80,
            rightLevel = -1,
            caseLevel = 90,
            caseBattery = CaseBattery(percent = 90, charging = true)
        )
        val summary = info.formatSummary(DeviceType.EARBUDS)
        assertEquals("Left: 80%  •  Case: 90% ⚡", summary)
        assertFalse(summary.contains("Right"))
    }

    @Test
    fun testUnifiedHeadphonesBattery() {
        val info = BatteryInfo(
            mainLevel = 70,
            leftLevel = -1,
            rightLevel = -1,
            caseLevel = -1
        )
        val summary = info.formatSummary(DeviceType.NECKBAND)
        assertEquals("Battery: 70%", summary)
        assertFalse(summary.contains("Left"))
        assertFalse(summary.contains("Right"))
    }

    @Test
    fun testUnknownBattery() {
        val info = BatteryInfo(
            mainLevel = -1,
            leftLevel = -1,
            rightLevel = -1,
            caseLevel = -1
        )
        assertFalse(info.isKnown)
        assertEquals("Connected", info.formatSummary(DeviceType.EARBUDS))
    }

    @Test
    fun testIsKnownTrueWhenAnyLevelValid() {
        assertTrue(BatteryInfo(leftLevel = 50).isKnown)
        assertTrue(BatteryInfo(rightLevel = 50).isKnown)
        assertTrue(BatteryInfo(mainLevel = 50).isKnown)
        assertTrue(BatteryInfo(caseLevel = 50).isKnown)
    }
}
