package com.smartbluetoothsleeptracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleepbt_prefs")

data class AppSettings(
    // Timer
    val selectedMinutes: Long = 30L,
    val extendMinutes: Int = 5,

    // Bluetooth
    val reconnectBlockerEnabled: Boolean = true,
    val cooldownSeconds: Int = 30,

    // Playback Control
    val playbackStopEnabled: Boolean = true,
    val fadeOutDurationSeconds: Int = 10,

    // Screen Off
    val screenOffEnabled: Boolean = false,

    // Haptic Feedback
    val hapticFeedbackEnabled: Boolean = true,

    // Notifications
    val sleepAlertsEnabled: Boolean = true,
    val warningLeadMinutes: Int = 2,
    val weeklySummaryEnabled: Boolean = false,
    val weeklySummaryHour: Int = 21, // 9 PM default
    val weeklySummaryDayOfWeek: Int = 7, // Sunday (ISO: 1=Mon..7=Sun)

    // Service
    val foregroundServiceEnabled: Boolean = true,

    // Appearance
    val themeMode: String = "DARK", // DARK, LIGHT, SYSTEM

    // Onboarding
    val onboardingComplete: Boolean = false,
    val tosAcceptedTimestamp: Long = 0L,
    val tosAcceptedVersion: String = "",

    // Presets
    val lastUsedPreset: Long = 0L,

    // Custom device categories (JSON array string)
    val customCategories: String = "[]",

    // Persisted timer state (survives process death)
    val timerEndWallClock: Long? = null,
    val timerPausedRemaining: Long? = null,
    val timerTargetDevices: String = "", // comma-separated MAC addresses
    val timerPlannedMinutes: Int = 0,
    val timerExtendedMinutes: Int = 0,
    val activeSessionId: Long = 0L
)

class AppPrefs(private val context: Context) {

    private val ds get() = context.dataStore

    val settings: Flow<AppSettings> = ds.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                selectedMinutes         = prefs[SELECTED_MINUTES] ?: 30L,
                extendMinutes           = prefs[EXTEND_MINUTES] ?: 5,
                reconnectBlockerEnabled = prefs[RECONNECT_BLOCKER] ?: true,
                cooldownSeconds         = prefs[COOLDOWN_SECONDS] ?: 30,
                playbackStopEnabled     = prefs[PLAYBACK_STOP] ?: true,
                fadeOutDurationSeconds  = prefs[FADE_DURATION] ?: 10,
                screenOffEnabled        = prefs[SCREEN_OFF] ?: false,
                hapticFeedbackEnabled   = prefs[HAPTIC_FEEDBACK] ?: true,
                sleepAlertsEnabled      = prefs[SLEEP_ALERTS] ?: true,
                warningLeadMinutes      = prefs[WARNING_LEAD] ?: 2,
                weeklySummaryEnabled    = prefs[WEEKLY_SUMMARY] ?: false,
                weeklySummaryHour       = prefs[WEEKLY_SUMMARY_HOUR] ?: 21,
                weeklySummaryDayOfWeek  = prefs[WEEKLY_SUMMARY_DAY] ?: 7,
                foregroundServiceEnabled= prefs[FG_SERVICE] ?: true,
                themeMode               = prefs[THEME_MODE] ?: "DARK",
                onboardingComplete      = prefs[ONBOARDING_COMPLETE] ?: false,
                tosAcceptedTimestamp    = prefs[TOS_TIMESTAMP] ?: 0L,
                tosAcceptedVersion      = prefs[TOS_VERSION] ?: "",
                lastUsedPreset          = prefs[LAST_USED_PRESET] ?: 0L,
                customCategories        = prefs[CUSTOM_CATEGORIES] ?: "[]",
                timerEndWallClock       = prefs[TIMER_END_WALL]?.takeIf { it > 0L },
                timerPausedRemaining    = prefs[TIMER_PAUSED]?.takeIf { it > 0L },
                timerTargetDevices      = prefs[TIMER_TARGETS] ?: "",
                timerPlannedMinutes     = prefs[TIMER_PLANNED] ?: 0,
                timerExtendedMinutes    = prefs[TIMER_EXTENDED] ?: 0,
                activeSessionId         = prefs[ACTIVE_SESSION_ID] ?: 0L
            )
        }

    // Setters
    suspend fun setSelectedMinutes(m: Long)       = ds.edit { it[SELECTED_MINUTES] = m.coerceIn(1, 480) }
    suspend fun setExtendMinutes(m: Int)          = ds.edit { it[EXTEND_MINUTES] = m.coerceIn(1, 60) }
    suspend fun setReconnectBlocker(on: Boolean)  = ds.edit { it[RECONNECT_BLOCKER] = on }
    suspend fun setCooldownSeconds(s: Int)        = ds.edit { it[COOLDOWN_SECONDS] = s.coerceIn(0, 120) }
    suspend fun setPlaybackStop(on: Boolean)      = ds.edit { it[PLAYBACK_STOP] = on }
    suspend fun setFadeOutDuration(s: Int)        = ds.edit { it[FADE_DURATION] = s.coerceIn(3, 30) }
    suspend fun setScreenOff(on: Boolean)         = ds.edit { it[SCREEN_OFF] = on }
    suspend fun setHapticFeedback(on: Boolean)    = ds.edit { it[HAPTIC_FEEDBACK] = on }
    suspend fun setSleepAlerts(on: Boolean)       = ds.edit { it[SLEEP_ALERTS] = on }
    suspend fun setWarningLeadMinutes(m: Int)     = ds.edit { it[WARNING_LEAD] = m.coerceIn(1, 10) }
    suspend fun setForegroundService(on: Boolean) = ds.edit { it[FG_SERVICE] = on }
    suspend fun setThemeMode(mode: String)        = ds.edit { it[THEME_MODE] = mode }
    suspend fun setOnboardingComplete(done: Boolean) = ds.edit { it[ONBOARDING_COMPLETE] = done }
    suspend fun setTosAccepted(ts: Long, version: String = CURRENT_TOS_VERSION) = ds.edit {
        it[TOS_TIMESTAMP] = ts
        it[TOS_VERSION] = version
    }
    suspend fun setLastUsedPreset(m: Long)       = ds.edit { it[LAST_USED_PRESET] = m }
    suspend fun setWeeklySummary(on: Boolean)     = ds.edit { it[WEEKLY_SUMMARY] = on }
    suspend fun setWeeklySummaryHour(h: Int)      = ds.edit { it[WEEKLY_SUMMARY_HOUR] = h.coerceIn(0, 23) }
    suspend fun setWeeklySummaryDay(d: Int)       = ds.edit { it[WEEKLY_SUMMARY_DAY] = d.coerceIn(1, 7) }
    suspend fun setCustomCategories(json: String) = ds.edit { it[CUSTOM_CATEGORIES] = json }

    // Timer persistence
    suspend fun setTimerEnd(ms: Long?)            = ds.edit { if (ms == null) it.remove(TIMER_END_WALL) else it[TIMER_END_WALL] = ms }
    suspend fun setTimerPaused(ms: Long?)         = ds.edit { if (ms == null) it.remove(TIMER_PAUSED) else it[TIMER_PAUSED] = ms }
    suspend fun setTimerTargets(targets: String)  = ds.edit { it[TIMER_TARGETS] = targets }
    suspend fun setTimerPlanned(min: Int)         = ds.edit { it[TIMER_PLANNED] = min }
    suspend fun setTimerExtended(min: Int)        = ds.edit { it[TIMER_EXTENDED] = min }
    suspend fun setActiveSessionId(id: Long)      = ds.edit { it[ACTIVE_SESSION_ID] = id }

    suspend fun clearTimer() = ds.edit {
        it.remove(TIMER_END_WALL)
        it.remove(TIMER_PAUSED)
        it.remove(TIMER_TARGETS)
        it.remove(TIMER_PLANNED)
        it.remove(TIMER_EXTENDED)
        it.remove(ACTIVE_SESSION_ID)
    }

    companion object {
        const val CURRENT_TOS_VERSION = "1.0.0"

        val SELECTED_MINUTES    = longPreferencesKey("selected_minutes")
        val EXTEND_MINUTES      = intPreferencesKey("extend_minutes")
        val RECONNECT_BLOCKER   = booleanPreferencesKey("reconnect_blocker")
        val COOLDOWN_SECONDS    = intPreferencesKey("cooldown_seconds")
        val PLAYBACK_STOP       = booleanPreferencesKey("playback_stop")
        val FADE_DURATION       = intPreferencesKey("fade_duration")
        val SCREEN_OFF          = booleanPreferencesKey("screen_off")
        val HAPTIC_FEEDBACK     = booleanPreferencesKey("haptic_feedback")
        val SLEEP_ALERTS        = booleanPreferencesKey("sleep_alerts")
        val WARNING_LEAD        = intPreferencesKey("warning_lead")
        val WEEKLY_SUMMARY      = booleanPreferencesKey("weekly_summary")
        val WEEKLY_SUMMARY_HOUR = intPreferencesKey("weekly_summary_hour")
        val WEEKLY_SUMMARY_DAY  = intPreferencesKey("weekly_summary_day")
        val FG_SERVICE          = booleanPreferencesKey("fg_service")
        val THEME_MODE          = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val TOS_TIMESTAMP       = longPreferencesKey("tos_timestamp")
        val TOS_VERSION         = stringPreferencesKey("tos_version")
        val LAST_USED_PRESET    = longPreferencesKey("last_used_preset")
        val CUSTOM_CATEGORIES   = stringPreferencesKey("custom_categories")
        val TIMER_END_WALL      = longPreferencesKey("timer_end_wall")
        val TIMER_PAUSED        = longPreferencesKey("timer_paused")
        val TIMER_TARGETS       = stringPreferencesKey("timer_targets")
        val TIMER_PLANNED       = intPreferencesKey("timer_planned")
        val TIMER_EXTENDED      = intPreferencesKey("timer_extended")
        val ACTIVE_SESSION_ID   = longPreferencesKey("active_session_id")
    }
}
