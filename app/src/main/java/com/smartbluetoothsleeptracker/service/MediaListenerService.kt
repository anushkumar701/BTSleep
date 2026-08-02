package com.smartbluetoothsleeptracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Stub NotificationListenerService. We don't actually process notifications —
 * this service exists solely so that MediaSessionManager.getActiveSessions()
 * can use our ComponentName to fetch active media sessions.
 *
 * The user must grant Notification Listener access in system settings for this to work.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* no-op */ }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
