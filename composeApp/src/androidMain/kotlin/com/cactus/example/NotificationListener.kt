package com.cactus.example

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val timestamp = sbn.postTime

        // Don't record notifications from our own app
        if (packageName == "com.cactus.example") return

        // Store the notification
        val notificationData = NotificationData(
            id = sbn.key,
            packageName = packageName,
            title = title,
            text = text,
            timestamp = timestamp
        )

        NotificationRepository.addNotification(notificationData)

        Log.d("NotificationListener", "Notification from $packageName: $title - $text")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
