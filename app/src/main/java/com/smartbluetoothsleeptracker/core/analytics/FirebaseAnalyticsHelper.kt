package com.smartbluetoothsleeptracker.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Centralized Firebase Analytics + Crashlytics helper.
 * All tracking events defined in the spec are dispatched through this class.
 */
class FirebaseAnalyticsHelper(private val analytics: FirebaseAnalytics) {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    // ── Timer Events ───────────────────────────────────────────────────────────

    fun trackTimerStarted(minutes: Long) {
        analytics.logEvent("timer_started") {
            putLong("duration_minutes", minutes)
        }
    }

    fun trackTimerCompleted(totalMinutes: Long) {
        analytics.logEvent("timer_completed") {
            putLong("total_minutes", totalMinutes)
        }
    }

    fun trackTimerExtended(extraMinutes: Long) {
        analytics.logEvent("timer_extended") {
            putLong("extra_minutes", extraMinutes)
        }
    }

    fun trackTimerCancelled(remainingMinutes: Long) {
        analytics.logEvent("timer_cancelled") {
            putLong("remaining_minutes", remainingMinutes)
        }
    }

    // ── Notification Events ────────────────────────────────────────────────────

    fun trackNotificationActionClicked(action: String) {
        analytics.logEvent("notification_action_clicked") {
            putString("action", action)
        }
    }

    // ── Crashlytics ────────────────────────────────────────────────────────────

    fun logDisconnectFailure(deviceName: String?, error: String) {
        crashlytics.recordException(
            RuntimeException("Bluetooth disconnect failed: $error (device: ${deviceName ?: "unknown"})")
        )
    }

    fun logNonFatal(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    /** Convenience for adding Bundle params */
    private fun FirebaseAnalytics.logEvent(name: String, builder: Bundle.() -> Unit) {
        val bundle = Bundle().apply(builder)
        logEvent(name, bundle)
    }
}
