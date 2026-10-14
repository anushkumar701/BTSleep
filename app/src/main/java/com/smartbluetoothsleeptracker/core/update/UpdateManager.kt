package com.smartbluetoothsleeptracker.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isAvailable: Boolean = false,
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val error: String? = null
)

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK_TS = "last_check_timestamp"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val GITHUB_REPO_URL = "https://api.github.com/repos/anushkumar701/BTSleep/releases/latest"
    }

    private val _updateState = MutableStateFlow(UpdateInfo())
    val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    suspend fun checkForUpdates(force: Boolean = false) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TS, 0L)
        val now = System.currentTimeMillis()

        if (!force && (now - lastCheck) < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Skipping update check — checked within the last 24h")
            return@withContext
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_REPO_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "SleepBT-App")

            if (connection.responseCode == 200) {
                prefs.edit().putLong(KEY_LAST_CHECK_TS, now).apply()
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val rawTag = json.optString("tag_name", "").removePrefix("v").trim()
                val notes = json.optString("body", "Bug fixes and performance improvements.").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/anushkumar701/BTSleep/releases")

                val currentVersion = getAppVersionName()
                if (isNewerVersion(rawTag, currentVersion)) {
                    _updateState.value = UpdateInfo(
                        isAvailable = true,
                        latestVersion = rawTag,
                        releaseNotes = notes,
                        downloadUrl = htmlUrl
                    )
                    Log.i(TAG, "Update available: $rawTag (current: $currentVersion)")
                } else {
                    _updateState.value = UpdateInfo(isAvailable = false)
                    Log.d(TAG, "App is up to date: $currentVersion")
                }
            } else {
                Log.d(TAG, "Update check response: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    fun openReleaseInBrowser(url: String) {
        val targetUrl = url.ifBlank { "https://github.com/anushkumar701/BTSleep/releases" }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
            Log.e(TAG, "Could not open browser for update URL: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = _updateState.value.copy(isAvailable = false)
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME
        }
    }

    private fun isNewerVersion(remoteVer: String, currentVer: String): Boolean {
        if (remoteVer.isBlank() || currentVer.isBlank()) return false
        val remoteParts = remoteVer.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVer.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
