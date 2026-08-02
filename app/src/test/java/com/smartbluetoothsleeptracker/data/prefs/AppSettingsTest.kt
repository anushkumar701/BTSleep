package com.smartbluetoothsleeptracker.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun testDefaultAppSettings() {
        val settings = AppSettings()
        assertEquals(30L, settings.selectedMinutes)
        assertEquals(5, settings.extendMinutes)
        assertTrue(settings.reconnectBlockerEnabled)
        assertEquals(30, settings.cooldownSeconds)
        assertTrue(settings.playbackStopEnabled)
        assertEquals(10, settings.fadeOutDurationSeconds)
        assertFalse(settings.screenOffEnabled)
        assertTrue(settings.hapticFeedbackEnabled)
        assertTrue(settings.sleepAlertsEnabled)
        assertEquals(2, settings.warningLeadMinutes)
        assertTrue(settings.foregroundServiceEnabled)
        assertEquals("DARK", settings.themeMode)
        assertFalse(settings.onboardingComplete)
        assertEquals(0L, settings.tosAcceptedTimestamp)
    }
}
