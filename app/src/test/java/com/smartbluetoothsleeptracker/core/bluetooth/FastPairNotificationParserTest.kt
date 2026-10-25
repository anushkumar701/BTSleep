package com.smartbluetoothsleeptracker.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPairNotificationParserTest {

    @Test
    fun testRealmeBudsT310_exactFormatFromDump() {
        val title = "Anush's realme Buds T310"
        val text = "Left 100% Case 80% Right 100%"
        val ticker = "Anush's realme Buds T310 Battery: Left 100% Case 80% Right 100%"

        val result = FastPairNotificationParser.parse(title, text, null, ticker)
        assertNotNull(result)
        assertEquals("Anush's realme Buds T310", result?.deviceName)
        assertEquals(100, result?.left)
        assertEquals(80, result?.case)
        assertEquals(100, result?.right)
        assertEquals(null, result?.isCaseCharging)
        assertTrue(result?.hasAnyBattery == true)

        // Verify device name matching
        assertTrue(FastPairNotificationParser.matchesDevice(result?.deviceName, "realme Buds T310"))
        assertTrue(FastPairNotificationParser.matchesDevice("Anush's realme Buds T310", "realme Buds T310"))
    }

    @Test
    fun testCaseWithColonsAndDifferentOrder() {
        val title = "Pixel Buds Pro"
        val text = "Case: 75%, Right: 85%, Left: 90%"

        val result = FastPairNotificationParser.parse(title, text)
        assertNotNull(result)
        assertEquals("Pixel Buds Pro", result?.deviceName)
        assertEquals(90, result?.left)
        assertEquals(85, result?.right)
        assertEquals(75, result?.case)
    }

    @Test
    fun testChargingCaseDetection() {
        val title = "OnePlus Buds Pro 2"
        val text = "Left 80% Case 95% (Charging) Right 80%"

        val result = FastPairNotificationParser.parse(title, text)
        assertNotNull(result)
        assertEquals(80, result?.left)
        assertEquals(80, result?.right)
        assertEquals(95, result?.case)
        assertEquals(true, result?.isCaseCharging)
    }

    @Test
    fun testShortLettersFormat() {
        val title = "Nothing Ear (2)"
        val text = "L: 70% | R: 70% | Case: 60%"

        val result = FastPairNotificationParser.parse(title, text)
        assertNotNull(result)
        assertEquals(70, result?.left)
        assertEquals(70, result?.right)
        assertEquals(60, result?.case)
    }

    @Test
    fun testOnlyBudsWithoutCase() {
        val title = "Galaxy Buds2"
        val text = "Left 50% Right 60%"

        val result = FastPairNotificationParser.parse(title, text)
        assertNotNull(result)
        assertEquals(50, result?.left)
        assertEquals(60, result?.right)
        assertEquals(-1, result?.case)
    }

    @Test
    fun testSingleDevicePercentage() {
        val title = "boAt Rockerz 255"
        val text = "Battery: 85%"

        val result = FastPairNotificationParser.parse(title, text)
        assertNotNull(result)
        assertEquals(85, result?.main)
        assertEquals(-1, result?.left)
        assertEquals(-1, result?.right)
        assertEquals(-1, result?.case)
    }

    @Test
    fun testIrrelevantNotificationDiscarded() {
        // Chat or email notifications must never be parsed as battery (Privacy guarantee)
        val title = "Mom"
        val text = "Hey, are you coming home for dinner at 8pm?"

        val result = FastPairNotificationParser.parse(title, text)
        assertNull(result)
    }

    @Test
    fun testDeviceNameMatchingVariants() {
        assertTrue(FastPairNotificationParser.matchesDevice("Anush's realme Buds T310", "realme Buds T310"))
        assertTrue(FastPairNotificationParser.matchesDevice("realme Buds T310", "Anush's realme Buds T310"))
        assertTrue(FastPairNotificationParser.matchesDevice("John’s AirPods Pro", "AirPods Pro"))
        assertFalse(FastPairNotificationParser.matchesDevice("Sony WH-1000XM4", "realme Buds T310"))
    }
}
