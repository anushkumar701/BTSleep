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
    val selectedMinutes: Long = 30L,
    val extendMinutes: Int = 10,
    val batterySaverEnabled: Boolean = false,
    val idleMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val themeMode: String = "DARK",
    val foregroundServiceEnabled: Boolean = true,
    val reconnectBlockerEnabled: Boolean = true,
    val timerEndWallClock: Long? = null,
    val timerPausedRemaining: Long? = null,
    val onboardingComplete: Boolean = false,
    val privacyAgreed: Boolean = false
)

class AppPrefs(private val context: Context) {

    private val ds get() = context.dataStore

    val settings: Flow<AppSettings> = ds.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                selectedMinutes       = prefs[SELECTED_MINUTES] ?: 30L,
                extendMinutes         = prefs[EXTEND_MINUTES] ?: 10,
                batterySaverEnabled   = prefs[BATTERY_SAVER] ?: false,
                idleMinutes           = prefs[IDLE_MINUTES] ?: 15,
                notificationsEnabled  = prefs[NOTIFICATIONS] ?: true,
                themeMode             = prefs[THEME_MODE] ?: "DARK",
                foregroundServiceEnabled = prefs[FG_SERVICE] ?: true,
                reconnectBlockerEnabled  = prefs[RECONNECT_BLOCKER] ?: true,
                timerEndWallClock     = prefs[TIMER_END_WALL]?.takeIf { it > 0L },
                timerPausedRemaining  = prefs[TIMER_PAUSED]?.takeIf { it > 0L },
                onboardingComplete    = prefs[ONBOARDING_COMPLETE] ?: false,
                privacyAgreed         = prefs[PRIVACY_AGREED] ?: false
            )
        }

    suspend fun setSelectedMinutes(minutes: Long)  = ds.edit { it[SELECTED_MINUTES] = minutes }
    suspend fun setExtendMinutes(minutes: Int)      = ds.edit { it[EXTEND_MINUTES] = minutes.coerceIn(5, 60) }
    suspend fun setBatterySaver(enabled: Boolean)   = ds.edit { it[BATTERY_SAVER] = enabled }
    suspend fun setIdleMinutes(minutes: Int)        = ds.edit { it[IDLE_MINUTES] = minutes }
    suspend fun setNotifications(enabled: Boolean)  = ds.edit { it[NOTIFICATIONS] = enabled }
    suspend fun setThemeMode(mode: String)          = ds.edit { it[THEME_MODE] = mode }
    suspend fun setForegroundService(enabled: Boolean) = ds.edit { it[FG_SERVICE] = enabled }
    suspend fun setReconnectBlocker(enabled: Boolean)  = ds.edit { it[RECONNECT_BLOCKER] = enabled }
    suspend fun setOnboardingComplete(done: Boolean)   = ds.edit { it[ONBOARDING_COMPLETE] = done }
    suspend fun setPrivacyAgreed(agreed: Boolean)      = ds.edit { it[PRIVACY_AGREED] = agreed }

    suspend fun setTimerEnd(wallClockMillis: Long?) = ds.edit {
        if (wallClockMillis == null) it.remove(TIMER_END_WALL) else it[TIMER_END_WALL] = wallClockMillis
    }
    suspend fun setTimerPaused(remainingMillis: Long?) = ds.edit {
        if (remainingMillis == null) it.remove(TIMER_PAUSED) else it[TIMER_PAUSED] = remainingMillis
    }
    suspend fun clearTimer() = ds.edit {
        it.remove(TIMER_END_WALL)
        it.remove(TIMER_PAUSED)
    }

    companion object {
        val SELECTED_MINUTES      = longPreferencesKey("selected_minutes")
        val EXTEND_MINUTES        = intPreferencesKey("extend_minutes")
        val BATTERY_SAVER         = booleanPreferencesKey("battery_saver")
        val IDLE_MINUTES          = intPreferencesKey("idle_minutes")
        val NOTIFICATIONS         = booleanPreferencesKey("notifications")
        val THEME_MODE            = stringPreferencesKey("theme_mode")
        val FG_SERVICE            = booleanPreferencesKey("fg_service")
        val RECONNECT_BLOCKER     = booleanPreferencesKey("reconnect_blocker")
        val TIMER_END_WALL        = longPreferencesKey("timer_end_wall")
        val TIMER_PAUSED          = longPreferencesKey("timer_paused")
        val ONBOARDING_COMPLETE   = booleanPreferencesKey("onboarding_complete")
        val PRIVACY_AGREED        = booleanPreferencesKey("privacy_agreed")
    }
}
