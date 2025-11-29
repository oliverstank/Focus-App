package com.cactus.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cactus.CactusLM
import com.cactus.CactusInitParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that runs every 5 minutes to process pending notifications in batches
 */
class NotificationProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "notification_processing_worker"
        const val TAG = "NotificationProcessingWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting batch notification processing")
            FocusModeRepository.updateProcessingStatus(ProcessingStatus.SCANNING, "Scanning for notifications...")

            // Get new pending notifications
            val pendingNotifications = FocusModeRepository.getPendingNotificationsAndClear()
            Log.d(TAG, "========================================")
            Log.d(TAG, "QUEUE BREAKDOWN:")
            Log.d(TAG, "  Pending notifications: ${pendingNotifications.size}")
            pendingNotifications.forEachIndexed { index, notif ->
                Log.d(TAG, "    [$index] ${notif.packageName}: ${notif.title}")
            }

            // Get only unimportant notifications for re-evaluation
            // Important notifications are kept as-is and not re-evaluated
            val unimportantNotifications = FocusModeRepository.unimportantNotifications.value.map { it.notification }
            Log.d(TAG, "  Unimportant for re-eval: ${unimportantNotifications.size}")

            // Combine new pending with existing unimportant for re-evaluation
            // Important notifications are never re-evaluated once categorized
            val beforeDistinct = pendingNotifications + unimportantNotifications
            Log.d(TAG, "  Combined before distinctBy: ${beforeDistinct.size}")

            val allNotifications = beforeDistinct.distinctBy { it.id }
            Log.d(TAG, "  After distinctBy (removed ${beforeDistinct.size - allNotifications.size} duplicates): ${allNotifications.size}")
            Log.d(TAG, "========================================")

            if (allNotifications.isEmpty()) {
                Log.d(TAG, "No notifications to process")
                FocusModeRepository.updateProcessingStatus(ProcessingStatus.IDLE, "No notifications to process")
                return@withContext Result.success()
            }

            Log.d(TAG, "SENDING TO LLM: ${allNotifications.size} notifications")
            FocusModeRepository.updateProcessingStatus(
                ProcessingStatus.FILTERING,
                "Processing ${allNotifications.size} notifications..."
            )

            // Initialize LLM
            var cactusLM: CactusLM? = null
            try {
                FocusModeRepository.updateProcessingStatus(ProcessingStatus.ANALYZING_WITH_LLM, "Initializing AI model...")

                cactusLM = CactusLM()
                if (!cactusLM.isLoaded()) {
                    Log.d(TAG, "Downloading and initializing LLM model")
                    FocusModeRepository.updateProcessingStatus(ProcessingStatus.ANALYZING_WITH_LLM, "Downloading AI model...")
                    cactusLM.downloadModel("qwen3-0.6")
                    cactusLM.initializeModel(CactusInitParams(model = "qwen3-0.6", contextSize = 1024))
                }

                // Create batch processor
                val batchProcessor = BatchNotificationProcessor(applicationContext, cactusLM)

                FocusModeRepository.updateProcessingStatus(
                    ProcessingStatus.ANALYZING_WITH_LLM,
                    "Analyzing ${allNotifications.size} notifications with AI..."
                )

                // Process all notifications in a single batch
                val importanceResults = batchProcessor.processBatch(allNotifications)

                // Categorize notifications based on importance
                val importantNotifications = allNotifications
                    .filterIndexed { index, _ -> importanceResults.getOrNull(index) == true }

                val unimportantNotifications = allNotifications
                    .filterIndexed { index, _ -> importanceResults.getOrNull(index) != true }

                Log.d(TAG, "Categorized: ${importantNotifications.size} important, ${unimportantNotifications.size} unimportant")

                // Get existing important notifications (we don't re-evaluate these)
                val existingImportantNotifications = FocusModeRepository.importantNotifications.value.map { it.notification }

                // Combine new important with existing important
                val allImportantNotifications = (existingImportantNotifications + importantNotifications).distinctBy { it.id }

                // Update repository with categorized notifications
                FocusModeRepository.recategorizeNotifications(
                    allImportantNotifications,
                    unimportantNotifications
                )

                Log.d(TAG, "Total important: ${allImportantNotifications.size} (${existingImportantNotifications.size} existing + ${importantNotifications.size} new)")

                // Show system notifications for newly categorized important notifications
                // (only show notifications that were just added to pending, not re-evaluated ones)
                val newlyImportant = importantNotifications.filter { notification ->
                    pendingNotifications.any { it.id == notification.id }
                }

                if (newlyImportant.isNotEmpty()) {
                    Log.d(TAG, "Showing ${newlyImportant.size} newly important notifications")
                    batchProcessor.showUrgentNotifications(newlyImportant)
                }

                FocusModeRepository.updateProcessingStatus(
                    ProcessingStatus.COMPLETE,
                    "Processed ${allNotifications.size} notifications"
                )

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notifications", e)
                // Re-queue new notifications on failure
                pendingNotifications.forEach { notification ->
                    FocusModeRepository.addPendingNotification(notification)
                }
                Result.retry()
            } finally {
                cactusLM?.unload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in worker", e)
            Result.failure()
        }
    }
}
