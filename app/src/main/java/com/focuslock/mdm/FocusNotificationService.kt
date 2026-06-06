package com.focuslock.mdm

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class FocusNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notifications are allowed in relaxed kiosk mode.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op: We don't need to do anything when a notification is naturally removed
    }
}