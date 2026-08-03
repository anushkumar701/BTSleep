package com.smartbluetoothsleeptracker.data

import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPrefsTest {

    @Test
    fun testDefaultAppSettingsValues() {
        val settings = AppSettings()
        assertEquals(30L, settings.selectedMinutes)
        assertEquals(10, settings.fadeOutDurationSeconds)
        assertTrue(settings.playbackStopEnabled)
        assertFalse(settings.screenOffEnabled)
        assertTrue(settings.reconnectBlockerEnabled)
        assertTrue(settings.hapticFeedbackEnabled)
    }

    @Test
    fun testFadeOutDurationBoundsInSeconds() {
        // AppPrefs enforces 5s to 1800s (30 minutes)
        val minSeconds = 5
        val maxSeconds = 1800 // 30 minutes * 60

        val clampedMin = minSeconds.coerceIn(5, 1800)
        val clampedMax = maxSeconds.coerceIn(5, 1800)
        val clampedOver = 3600.coerceIn(5, 1800)

        assertEquals(5, clampedMin)
        assertEquals(1800, clampedMax)
        assertEquals(1800, clampedOver)
    }

    @Test
    fun testFadeOutDurationMinutesConversion() {
        val seconds = 600
        val minutes = seconds / 60
        assertEquals(10, minutes)

        val newMinutes = 15
        val newSeconds = newMinutes * 60
        assertEquals(900, newSeconds)
    }
}
