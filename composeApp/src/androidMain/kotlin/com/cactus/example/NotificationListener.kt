package com.cactus.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cactusLM: CactusLM? = null
    private var priorityAnalyzer: NotificationPriorityAnalyzer? = null

    override fun onCreate() {
        super.onCreate()
        DumbModeRepository.initialize(this)

        // Initialize LLM for priority analysis
        serviceScope.launch(Dispatchers.IO) {
            try {
                cactusLM = CactusLM()
                if (!cactusLM!!.isLoaded()) {
                    cactusLM!!.downloadModel("qwen3-0.6")
                    cactusLM!!.initializeModel(CactusInitParams(model = "qwen3-0.6", contextSize = 1024))
                }
            } catch (e: Exception) {
                Log.e("NotificationListener", "Failed to initialize LLM", e)
            }
        }
    }

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

        // Handle dumb mode filtering
        val settings = DumbModeRepository.settings.value
        // If app is NOT whitelisted (i.e., blocked), handle the notification
        if (settings.isEnabled && packageName !in settings.whitelistedApps) {
            serviceScope.launch {
                handleDumbModeNotification(sbn, notificationData, settings)
            }
        }

        Log.d("NotificationListener", "Notification from $packageName: $title - $text")
    }

    private suspend fun handleDumbModeNotification(
        sbn: StatusBarNotification,
        notificationData: NotificationData,
        settings: DumbModeSettings
    ) {
        // Initialize analyzer if needed
        if (priorityAnalyzer == null) {
            priorityAnalyzer = NotificationPriorityAnalyzer(settings, cactusLM)
        }

        val priority = priorityAnalyzer!!.analyzePriority(notificationData)

        when (priority) {
            NotificationPriority.HIGH -> {
                // Show high priority notification with unlock option
                showPriorityNotification(notificationData)
            }
            NotificationPriority.LOW -> {
                // Cancel the original notification and queue it
                cancelNotification(sbn.key)
                DumbModeRepository.addQueuedNotification(notificationData, priority)
                Log.d("NotificationListener", "Queued low-priority notification from ${notificationData.packageName}")
            }
        }
    }

    private fun showPriorityNotification(notification: NotificationData) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel
        val channel = NotificationChannel(
            "priority_notifications",
            "Priority Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority notifications in dumb mode"
        }
        notificationManager.createNotificationChannel(channel)

        // Create intent to unlock the app temporarily
        val unlockIntent = Intent(this, UnlockReceiver::class.java).apply {
            putExtra("package_name", notification.packageName)
        }
        val unlockPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priorityNotification = NotificationCompat.Builder(this, "priority_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ PRIORITY: ${notification.title}")
            .setContentText(notification.text)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${notification.text}\n\nFrom: ${notification.packageName}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_lock_idle_lock,
                "Unlock App (5 min)",
                unlockPendingIntent
            )
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notification.id.hashCode(), priorityNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        cactusLM?.unload()
    }
}
