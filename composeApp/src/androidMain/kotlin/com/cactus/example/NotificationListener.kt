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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cactusLM: CactusLM? = null
    private var priorityAnalyzer: NotificationPriorityAnalyzer? = null

    override fun onCreate() {
        super.onCreate()
        FocusModeRepository.initialize(this)

        // Schedule periodic batch processing every 5 minutes
        scheduleNotificationProcessing()

        // Initialize LLM for priority analysis (used for immediate keyword checks)
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

    private fun scheduleNotificationProcessing() {
        val workRequest = PeriodicWorkRequestBuilder<NotificationProcessingWorker>(
            3, TimeUnit.MINUTES,
            1, TimeUnit.MINUTES // flex interval
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            NotificationProcessingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Set next scan time (3 minutes from now)
        val nextScanTime = System.currentTimeMillis() + (3 * 60 * 1000)
        FocusModeRepository.setNextScanTime(nextScanTime)

        Log.d("NotificationListener", "Scheduled periodic notification processing every 3 minutes")
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

        Log.d("NotificationListener", "========================================")
        Log.d("NotificationListener", "NOTIFICATION RECEIVED:")
        Log.d("NotificationListener", "  Package: $packageName")
        Log.d("NotificationListener", "  Title: $title")
        Log.d("NotificationListener", "  Text: $text")
        Log.d("NotificationListener", "  Focus Mode Enabled: ${FocusModeRepository.settings.value.isEnabled}")
        Log.d("NotificationListener", "========================================")

        // ALWAYS queue notification for batch processing (for testing)
        // In production, you would check focus mode settings
        FocusModeRepository.addPendingNotification(notificationData)
        Log.d("NotificationListener", "✓ Notification QUEUED for batch processing")

        // Handle focus mode filtering (only cancel if focus mode is enabled)
        val settings = FocusModeRepository.settings.value
        if (settings.isEnabled && packageName !in settings.whitelistedApps) {
            // Cancel the original notification so user doesn't see it until it's processed
            // Use a small delay to avoid race conditions
            serviceScope.launch(Dispatchers.Main) {
                try {
                    kotlinx.coroutines.delay(100) // Small delay to avoid race condition
                    cancelNotification(sbn.key)
                    Log.d("NotificationListener", "✓ Cancelled original notification from $packageName")
                } catch (e: Exception) {
                    Log.e("NotificationListener", "✗ Failed to cancel notification", e)
                }
            }
        } else {
            Log.d("NotificationListener", "⚠ Focus mode disabled or app whitelisted - notification NOT cancelled")
        }
    }

    private suspend fun handleFocusModeNotification(
        sbn: StatusBarNotification,
        notificationData: NotificationData,
        settings: FocusModeSettings
    ) {
        // Initialize analyzer if needed
        if (priorityAnalyzer == null) {
            priorityAnalyzer = NotificationPriorityAnalyzer(settings, cactusLM)
        }

        val priority = priorityAnalyzer!!.analyzePriority(notificationData)

        when (priority) {
            NotificationPriority.HIGH -> {
                // Cancel the original notification
                cancelNotification(sbn.key)
                // Show high priority notification with unlock option
                showPriorityNotification(notificationData)
            }
            NotificationPriority.LOW -> {
                // Cancel the original notification and queue it
                cancelNotification(sbn.key)
                FocusModeRepository.addQueuedNotification(notificationData, priority)
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
            description = "High priority notifications in focus mode"
        }
        notificationManager.createNotificationChannel(channel)

        // Create intent to unlock the app temporarily
        val unlockIntent = Intent(this, UnlockReceiver::class.java).apply {
            putExtra("package_name", notification.packageName)
        }
        val unlockPendingIntent = PendingIntent.getBroadcast(
            this,
            notification.id.hashCode(),
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
                "Use Once",
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
