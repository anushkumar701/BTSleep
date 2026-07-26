package com.smartbluetoothsleeptracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeFormatterUtilTest {

    @Test
    fun `formatDuration renders minutes only`() {
        assertEquals("45 min", DateTimeFormatterUtil.formatDuration(45 * 60_000L))
    }

    @Test
    fun `formatDuration renders hours and minutes`() {
        assertEquals("2 hr 15 min", DateTimeFormatterUtil.formatDuration(135 * 60_000L))
    }

    @Test
    fun `formatMinutesOfDay renders 12 hour clock`() {
        assertEquals("11:05 PM", DateTimeFormatterUtil.formatMinutesOfDay(23 * 60 + 5))
    }

    @Test
    fun `formatCountdown renders hours minutes and seconds`() {
        assertEquals("01:01:05", DateTimeFormatterUtil.formatCountdown(3_665_000L))
    }
}
