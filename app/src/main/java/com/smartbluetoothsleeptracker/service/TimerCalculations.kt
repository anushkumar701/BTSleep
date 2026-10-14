package com.smartbluetoothsleeptracker.service

/**
 * Pure calculation functions for sleep timer math, session duration accounting,
 * auto-extend limits, and pause/resume tracking.
 */
object TimerCalculations {

    const val MAX_AUTO_EXTEND_MINUTES = 30
    const val MIN_DIAL_MINUTES = 1L
    const val MAX_DIAL_MINUTES = 120L

    /**
     * Calculates remaining time in milliseconds given a target end wall-clock time and current time.
     */
    fun calculateRemainingMs(endTimeMillis: Long, now: Long): Long {
        return (endTimeMillis - now).coerceAtLeast(0L)
    }

    /**
     * Determines if a persisted timer has expired based on its wall-clock end time.
     */
    fun isTimerExpired(endTimeMillis: Long, now: Long): Boolean {
        return endTimeMillis <= now
    }

    /**
     * Calculates total active session duration in milliseconds, excluding paused periods.
     */
    fun calculateActiveDurationMs(sessionStartTime: Long, sessionEndTime: Long, totalPausedMs: Long): Long {
        if (sessionStartTime <= 0L || sessionEndTime < sessionStartTime) return 0L
        return (sessionEndTime - sessionStartTime - totalPausedMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Converts active duration in milliseconds to rounded/accurate integer minutes for database logging.
     * Sessions under 30 seconds (e.g. quick cancel or accidental trigger) are 0 minutes.
     * Active durations >= 30 seconds are rounded to the nearest minute.
     */
    fun calculateDurationMinutes(activeDurationMs: Long): Int {
        if (activeDurationMs < 30_000L) return 0
        return ((activeDurationMs + 30_000L) / 60_000L).toInt()
    }

    /**
     * Calculates accumulated paused time when resuming from a paused state.
     */
    fun calculatePausedAccumulation(pauseStartTime: Long, now: Long): Long {
        if (pauseStartTime > 0L && now > pauseStartTime) {
            return now - pauseStartTime
        }
        return 0L
    }

    /**
     * Calculates new wall-clock end time upon resuming a paused timer.
     */
    fun calculateResumeEndTime(now: Long, pausedRemainingMs: Long): Long {
        return now + pausedRemainingMs.coerceAtLeast(0L)
    }

    /**
     * Checks whether auto-extension is permitted under the maximum cap.
     */
    fun canAutoExtend(extendedMinutes: Int, maxLimit: Int = MAX_AUTO_EXTEND_MINUTES): Boolean {
        return extendedMinutes < maxLimit
    }

    /**
     * Clamps a minute value to the supported rotary dial bounds (1 to 120 minutes).
     */
    fun clampMinutes(minutes: Long): Long {
        return minutes.coerceIn(MIN_DIAL_MINUTES, MAX_DIAL_MINUTES)
    }

    /**
     * Formats remaining lead time into human-friendly minutes or seconds for warning notifications.
     * Prevents displaying "2m left" when approaching expiration on a 1-minute timer (e.g. 30s remaining).
     */
    fun formatRemainingLeadText(remainingMs: Long): String {
        val remSec = (remainingMs / 1000L).coerceAtLeast(0L)
        return if (remSec < 60L) "${remSec}s left" else "${(remSec + 59L) / 60L}m left"
    }
}
