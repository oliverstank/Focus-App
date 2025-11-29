package com.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cactus.CactusLM
import com.cactus.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatchNotificationProcessor(
    private val context: Context,
    private val cactusLM: CactusLM?
) {

    /**
     * Process a batch of notifications using the LLM to determine urgency
     * @param notifications List of notifications to process
     * @return List of notification indices that are urgent (true = urgent, false = not urgent)
     */
    suspend fun processBatch(notifications: List<NotificationData>): List<Boolean> {
        if (notifications.isEmpty()) {
            return emptyList()
        }

        // If LLM is not available, fall back to keyword-based filtering
        if (cactusLM == null || !cactusLM.isLoaded()) {
            Log.w("BatchNotificationProcessor", "LLM not available, using fallback")
            return notifications.map { containsUrgentKeywords(it) }
        }

        return analyzeWithLLM(notifications)
    }

    private suspend fun analyzeWithLLM(notifications: List<NotificationData>): List<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // Build a prompt with all notifications
                val prompt = buildBatchPrompt(notifications)

                Log.d("BatchNotificationProcessor", "========================================")
                Log.d("BatchNotificationProcessor", "SENDING TO LLM - Analyzing ${notifications.size} notifications")
                Log.d("BatchNotificationProcessor", "Prompt:\n$prompt")
                Log.d("BatchNotificationProcessor", "========================================")

                val result = cactusLM?.generateCompletion(
                    messages = listOf(ChatMessage(content = prompt, role = "user"))
                )

                val response = result?.response?.trim() ?: ""

                Log.d("BatchNotificationProcessor", "========================================")
                Log.d("BatchNotificationProcessor", "LLM RESPONSE RECEIVED:")
                Log.d("BatchNotificationProcessor", "Raw response: $response")
                Log.d("BatchNotificationProcessor", "Response length: ${response.length} characters")
                Log.d("BatchNotificationProcessor", "========================================")

                // Parse the response to extract true/false values
                val parsedResults = parseUrgencyResponse(response, notifications.size)

                Log.d("BatchNotificationProcessor", "PARSED RESULTS:")
                parsedResults.forEachIndexed { index, isImportant ->
                    val notification = notifications.getOrNull(index)
                    Log.d("BatchNotificationProcessor",
                        "  [$index] ${if (isImportant) "IMPORTANT" else "IGNORED"} - ${notification?.title}")
                }
                Log.d("BatchNotificationProcessor", "========================================")

                parsedResults
            } catch (e: Exception) {
                Log.e("BatchNotificationProcessor", "LLM analysis failed", e)
                Log.e("BatchNotificationProcessor", "Falling back to keyword-based filtering")
                // Fallback to keyword-based filtering
                notifications.map { containsUrgentKeywords(it) }
            }
        }
    }

    private fun buildBatchPrompt(notifications: List<NotificationData>): String {
        val notificationList = notifications.mapIndexed { index, notification ->
            """
            Notification ${index + 1}:
            App: ${notification.packageName}
            Title: ${notification.title}
            Message: ${notification.text}
            """.trimIndent()
        }.joinToString("\n\n")

        return """
            You are a notification filter. Classify each notification as URGENT (true) or NOT URGENT (false).

            URGENT (true) means:
            - Emergency messages: "help", "danger", "emergency", "urgent", "SOS", "911" or similar. 
            - Work-critical alerts (server down, security breach) or similar.
            - Messages containing: "ASAP", "immediately", "critical", "important call" or similar.

            NOT URGENT (false) means:
            - Promotional offers: "free", "discount", "sale", "congratulations"
            - Music/media notifications (Spotify, YouTube, Amazon Music)
            - Social media likes/comments (Instagram, Facebook, Twitter)
            - App updates or system notifications
            - Marketing messages
            - Generic app notifications
           

            EXAMPLES:
            - "Help I am in danger" → true (emergency)
            - "Congratulations! Free music for 4 months" → false (promotional)
            - "Meeting in 10 minutes" → true (time-sensitive)
            - "Your package has shipped" → false (informational)
            - "Server load at 95%" → true (work-critical)
            - "Now playing: Song Name" → false (media playback)

            $notificationList

            RESPOND WITH ONLY COMMA-SEPARATED true/false VALUES. NO EXPLANATION.

            For ${notifications.size} notifications, respond with exactly ${notifications.size} comma-separated values.

            Your response:
        """.trimIndent()
    }

    /**
     * Parse the LLM response to extract true/false values
     * Expected format: "true,false,true,false" or "true, false, true, false"
     */
    private fun parseUrgencyResponse(response: String, expectedCount: Int): List<Boolean> {
        try {
            // Remove <think> tags if present
            var cleanedResponse = response
                .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                .trim()

            // Remove special tokens like <|im_end|>
            cleanedResponse = cleanedResponse
                .replace(Regex("<\\|.*?\\|>"), "")
                .trim()

            // Find lines that look like CSV responses (true/false separated by commas)
            val lines = cleanedResponse.lines()
            val csvLine = lines.firstOrNull { line ->
                val trimmed = line.trim().lowercase()
                trimmed.matches(Regex("^(true|false)(\\s*,\\s*(true|false))+$"))
            }

            if (csvLine != null) {
                cleanedResponse = csvLine
            } else {
                // Try to extract the last occurrence of a CSV pattern
                val csvPattern = Regex("(true|false)(\\s*,\\s*(true|false))+", RegexOption.IGNORE_CASE)
                val match = csvPattern.findAll(cleanedResponse).lastOrNull()
                if (match != null) {
                    cleanedResponse = match.value
                }
            }

            Log.d("BatchNotificationProcessor", "Extracted CSV line: $cleanedResponse")

            // Extract the values (comma-separated OR whitespace-separated)
            val values = cleanedResponse
                .lowercase()
                .replace(Regex("\\s+"), ",") // Replace whitespace with commas first
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            // Convert to boolean
            val booleans = values.mapNotNull { value ->
                when {
                    value.contains("true") -> true
                    value.contains("false") -> false
                    else -> null // Skip unclear values
                }
            }

            Log.d("BatchNotificationProcessor", "Extracted ${booleans.size} values: $booleans")

            // Validate we got the expected number of responses
            if (booleans.size >= expectedCount) {
                // Take exactly the number we need
                return booleans.take(expectedCount)
            } else {
                Log.w("BatchNotificationProcessor",
                    "Expected $expectedCount responses, got ${booleans.size}. Padding with false.")

                // If we got fewer responses, pad with false
                return booleans + List(expectedCount - booleans.size) { false }
            }
        } catch (e: Exception) {
            Log.e("BatchNotificationProcessor", "Failed to parse response: $response", e)
            // Return all false as fallback
            return List(expectedCount) { false }
        }
    }

    private fun containsUrgentKeywords(notification: NotificationData): Boolean {
        val text = "${notification.title} ${notification.text}".lowercase()
        val urgentKeywords = setOf(
            "urgent", "emergency", "asap", "important", "critical",
            "911", "help", "sos", "alert", "warning", "deadline",
            "immediately", "now", "attention required"
        )
        return urgentKeywords.any { keyword -> text.contains(keyword) }
    }

    /**
     * Show urgent notifications to the user
     */
    fun showUrgentNotifications(urgentNotifications: List<NotificationData>) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel
        val channel = NotificationChannel(
            "urgent_notifications",
            "Urgent Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent notifications filtered by AI"
        }
        notificationManager.createNotificationChannel(channel)

        // Show each urgent notification
        urgentNotifications.forEach { notification ->
            showUrgentNotification(notification, notificationManager)
        }
    }

    private fun showUrgentNotification(
        notification: NotificationData,
        notificationManager: NotificationManager
    ) {
        // Create intent to unlock the app temporarily
        val unlockIntent = Intent(context, UnlockReceiver::class.java).apply {
            putExtra("package_name", notification.packageName)
        }
        val unlockPendingIntent = PendingIntent.getBroadcast(
            context,
            notification.id.hashCode(),
            unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val urgentNotification = NotificationCompat.Builder(context, "urgent_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ URGENT: ${notification.title}")
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

        notificationManager.notify(notification.id.hashCode(), urgentNotification)
    }
}
