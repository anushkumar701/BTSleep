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

            // 3. Perform Anonymous Authentication
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

    /**
     * Silently retrieves FCM registration token and logs it to Analytics & Firestore fcm_tokens collection.
     */
    private fun registerFcmTokenSilently(context: Context) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                Log.i(TAG, "FCM Token retrieved silently: $token")

                // Set as user property in Analytics
                val analytics = FirebaseAnalytics.getInstance(context)
                analytics.setUserProperty("fcm_token", token)

                // Log custom event fcm_token_registered
                val bundle = Bundle().apply {
                    putString("fcm_token", token)
                }
                analytics.logEvent("fcm_token_registered", bundle)

                // Save to Firestore fcm_tokens collection for server-side uninstall tracking
                val db = FirebaseFirestore.getInstance()
                val tokenDoc = db.collection(COLLECTION_FCM_TOKENS).document(token)
                val tokenData = mapOf(
                    "token" to token,
                    "status" to "active",
                    "deviceName" to getFormattedDeviceName(),
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "model" to android.os.Build.MODEL,
                    "androidVersion" to android.os.Build.VERSION.RELEASE,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tokenDoc.set(tokenData, SetOptions.merge())
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
     * Creates or updates the device document in Firestore collection 'devices'.
     * Sets installedAt on first creation and updates lastActiveAt on every launch.
     */
    private fun recordDeviceActivity(context: Context, uid: String) {
        try {
            val formattedName = getFormattedDeviceName()
            val manufacturer = android.os.Build.MANUFACTURER
            val model = android.os.Build.MODEL
            val osVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

            // Log properties in Firebase Analytics for easy filtering
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.setUserProperty("device_name", formattedName)
            analytics.setUserProperty("device_model", model)
            analytics.setUserProperty("android_version", osVersion)

            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection(COLLECTION_DEVICES).document(uid)

            docRef.get().addOnSuccessListener { snapshot ->
                val data = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "deviceName" to formattedName,
                    "manufacturer" to manufacturer,
                    "model" to model,
                    "androidVersion" to android.os.Build.VERSION.RELEASE,
                    "sdkVersion" to android.os.Build.VERSION.SDK_INT,
                    "osVersion" to osVersion,
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
                if (!snapshot.exists()) {
                    data["installedAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.i(TAG, "Firestore device document updated for $formattedName ($uid)")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed updating Firestore device doc: ${e.message}")
                    }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed reading Firestore device doc: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording device activity in Firestore: ${e.message}")
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
