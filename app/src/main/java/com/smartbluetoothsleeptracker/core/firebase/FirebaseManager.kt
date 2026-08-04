package com.smartbluetoothsleeptracker.core.firebase

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private const val COLLECTION_DEVICES = "devices"
    private const val COLLECTION_FCM_TOKENS = "fcm_tokens"

    /**
     * Initializes Firebase Anonymous Auth and updates/creates device document in Firestore.
     * Also logs an initial app_session event to Firebase Analytics and silently registers FCM token.
     */
    fun initAndTrackDevice(context: Context) {
        try {
            // 1. Log custom app session event in Firebase Analytics
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.logEvent("app_session", null)
            Log.d(TAG, "Firebase Analytics logged app_session event")

            // 2. Silently register FCM token for uninstall tracking
            registerFcmTokenSilently(context)

            // 3. Immediately record device activity using persistent hardware/UUID device ID (independent of Auth success)
            recordDeviceActivity(context, null)

            // 4. Perform Anonymous Authentication concurrently
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            if (currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { authResult ->
                        val uid = authResult.user?.uid ?: return@addOnSuccessListener
                        Log.i(TAG, "Anonymous sign-in successful: $uid")
                        recordDeviceActivity(context, uid)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Anonymous sign-in failed: ${e.message}")
                    }
            } else {
                Log.d(TAG, "Device already authenticated anonymously: ${currentUser.uid}")
                recordDeviceActivity(context, currentUser.uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase tracking: ${e.message}")
        }
    }

    private fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("app_firebase_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("unique_device_id", null)
        if (id.isNullOrBlank()) {
            val androidId = try {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
            id = if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                androidId
            } else {
                java.util.UUID.randomUUID().toString()
            }
            prefs.edit().putString("unique_device_id", id).apply()
        }
        return id
    }

    /**
     * Silently retrieves FCM registration token and saves it to Firestore for uninstall tracking.
     */
    private fun registerFcmTokenSilently(context: Context) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                Log.i(TAG, "FCM Token retrieved silently")

                // Save to Firestore fcm_tokens — only what's needed for uninstall tracking
                val db = FirebaseFirestore.getInstance()
                val tokenData = mapOf(
                    "token" to token,
                    "status" to "active",
                    "deviceId" to getDeviceId(context),
                    "deviceName" to getFormattedDeviceName(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(COLLECTION_FCM_TOKENS).document(token)
                    .set(tokenData, SetOptions.merge())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerFcmTokenSilently: ${e.message}")
        }
    }

    private fun getFormattedDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() 
        }
        val model = android.os.Build.MODEL
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Creates or updates the device document in Firestore — stores only device name.
     */
    private fun recordDeviceActivity(context: Context, authUid: String?) {
        try {
            val deviceId = getDeviceId(context)
            val deviceName = getFormattedDeviceName()

            // Only log device_name to Analytics — no hardware fingerprinting
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.setUserProperty("device_name", deviceName)

            val db = FirebaseFirestore.getInstance()

            val data = mutableMapOf<String, Any>(
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "lastActiveAt" to FieldValue.serverTimestamp()
            )
            if (authUid != null) {
                data["authUid"] = authUid
            }

            // Write to device ID document
            val deviceDocRef = db.collection(COLLECTION_DEVICES).document(deviceId)
            deviceDocRef.set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.i(TAG, "Firestore device document updated for $deviceName ($deviceId)")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed updating Firestore device doc: ${e.message}")
                }

            // If Auth UID available, also mirror to authUid doc
            if (!authUid.isNullOrEmpty()) {
                db.collection(COLLECTION_DEVICES).document(authUid)
                    .set(data, SetOptions.merge())
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed updating auth doc: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording device activity: ${e.message}")
        }
    }

    /**
     * Logs home_tab_loaded event to Firebase Analytics when the Home screen is displayed.
     */
    fun logHomeScreenLoad(context: Context) {
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.logEvent("home_tab_loaded", null)
            Log.d(TAG, "Logged home_tab_loaded event")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging home_tab_loaded event: ${e.message}")
        }
    }
}
