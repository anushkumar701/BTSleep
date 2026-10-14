package com.smartbluetoothsleeptracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying pure math functions in TimerCalculations:
 * remaining time, expiry checks, active duration accounting (excluding pauses),
 * auto-extend limits, and dial clamping.
 */
class TimerStateCalculationTest {

    @Test
    fun testProcessDeath_wallClockInFuture_restoresRemainingCorrectly() {
        val now = 1_000_000L
        val savedEndWallClock = now + 15 * 60_000L // 15 min remaining

        val remainingMs = TimerCalculations.calculateRemainingMs(savedEndWallClock, now)
        val isExpired = TimerCalculations.isTimerExpired(savedEndWallClock, now)

        assertEquals(15 * 60_000L, remainingMs)
        assertFalse(isExpired)
    }

    @Test
    fun testProcessDeath_wallClockInPast_triggersImmediateExpiry() {
        val now = 1_000_000L
        val savedEndWallClock = now - 5_000L // Passed 5s ago

        val remainingMs = TimerCalculations.calculateRemainingMs(savedEndWallClock, now)
        val isExpired = TimerCalculations.isTimerExpired(savedEndWallClock, now)

        assertEquals(0L, remainingMs)
        assertTrue(isExpired)
    }

    @Test
    fun testPauseResume_activeDurationExcludesPausedDuration() {
        val sessionStartTime = 1_000_000L
        val pauseStartTime = sessionStartTime + 10 * 60_000L // Paused after 10m
        val resumeTime = pauseStartTime + 5 * 60_000L // Paused for 5m

        val pausedAccumulation = TimerCalculations.calculatePausedAccumulation(pauseStartTime, resumeTime)
        assertEquals(5 * 60_000L, pausedAccumulation)

        val sessionEndTime = resumeTime + 20 * 60_000L // Ran for another 20m

        // Active duration excluding 5m pause = 30 min (10m + 20m)
        val totalActiveMs = TimerCalculations.calculateActiveDurationMs(sessionStartTime, sessionEndTime, pausedAccumulation)
        val actualMinutes = TimerCalculations.calculateDurationMinutes(totalActiveMs)

        assertEquals(30 * 60_000L, totalActiveMs)
        assertEquals(30, actualMinutes)
    }

    @Test
    fun testPauseResume_resumeEndTimeCalculatedCorrectly() {
        val now = 2_000_000L
        val remainingMs = 12 * 60_000L
        val newEndWallClock = TimerCalculations.calculateResumeEndTime(now, remainingMs)
        assertEquals(2_000_000L + 12 * 60_000L, newEndWallClock)
    }

    @Test
    fun testAutoExtendCap_max30Minutes() {
        var extendedMinutes = 0
        val extendIncrement = 5

        var extendCount = 0
        for (i in 1..7) {
            if (TimerCalculations.canAutoExtend(extendedMinutes)) {
                extendedMinutes += extendIncrement
                extendCount++
            }
        }

        // 30 / 5 = 6 extensions allowed (total 30 min), 7th is blocked
        assertEquals(6, extendCount)
        assertEquals(30, extendedMinutes)
        assertFalse(TimerCalculations.canAutoExtend(extendedMinutes))
    }

    @Test
    fun testTimerDialSnapAndClamp() {
        assertEquals(1L, TimerCalculations.clampMinutes(-5L))
        assertEquals(1L, TimerCalculations.clampMinutes(0L))
        assertEquals(1L, TimerCalculations.clampMinutes(1L))
        assertEquals(45L, TimerCalculations.clampMinutes(45L))
        assertEquals(120L, TimerCalculations.clampMinutes(120L))
        assertEquals(120L, TimerCalculations.clampMinutes(150L))
    }

    @Test
    fun testCalculateDurationMinutes_accurateRoundingAndCancellation() {
        // Sub-30s cancels are 0 minutes
        assertEquals(0, TimerCalculations.calculateDurationMinutes(0L))
        assertEquals(0, TimerCalculations.calculateDurationMinutes(15_000L))
        assertEquals(0, TimerCalculations.calculateDurationMinutes(29_999L))

        // >= 30s rounds to 1 minute
        assertEquals(1, TimerCalculations.calculateDurationMinutes(30_000L))
        assertEquals(1, TimerCalculations.calculateDurationMinutes(45_000L))
        assertEquals(1, TimerCalculations.calculateDurationMinutes(60_000L))
        assertEquals(1, TimerCalculations.calculateDurationMinutes(89_999L))

        // Multi-minute accurate rounding
        assertEquals(2, TimerCalculations.calculateDurationMinutes(90_000L))
        assertEquals(30, TimerCalculations.calculateDurationMinutes(30 * 60_000L))
        assertEquals(30, TimerCalculations.calculateDurationMinutes(30 * 60_000L + 25_000L))
        assertEquals(31, TimerCalculations.calculateDurationMinutes(30 * 60_000L + 35_000L))
    }

    @Test
    fun testFormatRemainingLeadText_shortDurations_displaysSecondsNotMinutes() {
        // 30 seconds on a 1-minute timer must display 30s left, not 2m left
        assertEquals("30s left", TimerCalculations.formatRemainingLeadText(30_000L))
        assertEquals("15s left", TimerCalculations.formatRemainingLeadText(15_000L))
        assertEquals("59s left", TimerCalculations.formatRemainingLeadText(59_000L))
        assertEquals("0s left", TimerCalculations.formatRemainingLeadText(0L))
    }

    @Test
    fun testFormatRemainingLeadText_longDurations_displaysMinutes() {
        assertEquals("1m left", TimerCalculations.formatRemainingLeadText(60_000L))
        assertEquals("2m left", TimerCalculations.formatRemainingLeadText(120_000L))
        assertEquals("2m left", TimerCalculations.formatRemainingLeadText(90_000L))
    }
}
