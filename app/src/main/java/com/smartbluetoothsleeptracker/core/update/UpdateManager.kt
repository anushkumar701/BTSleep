package com.smartbluetoothsleeptracker.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

    private val _updateState = MutableStateFlow(UpdateInfo())
    val updateState: StateFlow<UpdateInfo> = _updateState.asStateFlow()

    private val githubRepoUrl = "https://api.github.com/repos/anushkumar701/BTSleep/releases/latest"

    suspend fun checkForUpdates() = withContext(Dispatchers.IO) {
        try {
            val url = URL(githubRepoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "SleepBT-App")

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val rawTag = json.optString("tag_name", "").removePrefix("v").trim()
                val notes = json.optString("body", "Bug fixes and performance improvements.").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/anushkumar701/BTSleep/releases")

                // Find APK asset URL
                var apkUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val currentVersion = getAppVersionName()
                if (isNewerVersion(rawTag, currentVersion)) {
                    _updateState.value = UpdateInfo(
                        isAvailable = true,
                        latestVersion = rawTag,
                        releaseNotes = notes,
                        downloadUrl = apkUrl
                    )
                } else {
                    _updateState.value = UpdateInfo(isAvailable = false)
                }
            } else {
                Log.d("UpdateManager", "No release found or code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Update check failed: ${e.message}")
        }
    }

    suspend fun downloadAndInstallApk(apkUrl: String) = withContext(Dispatchers.IO) {
        if (apkUrl.isBlank()) return@withContext

        try {
            _updateState.value = _updateState.value.copy(isDownloading = true, downloadProgress = 0f, error = null)

            val url = URL(apkUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.connect()

            val fileLength = connection.contentLength
            val apkFile = File(context.cacheDir, "SleepBT_update.apk")
            if (apkFile.exists()) apkFile.delete()

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val progress = (total.toFloat() / fileLength.toFloat())
                            _updateState.value = _updateState.value.copy(downloadProgress = progress)
                        }
                    }
                }
            }

            _updateState.value = _updateState.value.copy(isDownloading = false, downloadProgress = 1.0f)
            promptInstallApk(apkFile)

        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to download APK: ${e.message}")
            _updateState.value = _updateState.value.copy(
                isDownloading = false,
                error = "Direct download failed. Opening browser release page..."
            )
            // Fallback: Open browser to download directly
            openBrowserFallback(apkUrl)
        }
    }

    fun promptInstallApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Install prompt failed: ${e.message}")
            openBrowserFallback(_updateState.value.downloadUrl)
        }
    }

    fun openBrowserFallback(urlStr: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to open browser: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateInfo(isAvailable = false)
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val rVal = rParts.getOrElse(i) { 0 }
            val cVal = cParts.getOrElse(i) { 0 }
            if (rVal > cVal) return true
            if (rVal < cVal) return false
        }
        return false
    }
}
