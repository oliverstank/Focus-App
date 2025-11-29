package com.focus

import com.cactus.CactusLM
import com.cactus.CactusInitParams
import com.cactus.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationPriorityAnalyzer(
    private val settings: FocusModeSettings,
    private val cactusLM: CactusLM? = null
) {

    suspend fun analyzePriority(notification: NotificationData): NotificationPriority {
        // 1. Check user overrides first (highest priority)
        if (notification.packageName in settings.alwaysPriorityApps) {
            return NotificationPriority.HIGH
        }
        if (notification.packageName in settings.neverPriorityApps) {
            return NotificationPriority.LOW
        }

        // 2. Check for priority keywords
        if (containsPriorityKeywords(notification)) {
            return NotificationPriority.HIGH
        }

        // 3. Check for priority contacts (if available in notification)
        if (isFromPriorityContact(notification)) {
            return NotificationPriority.HIGH
        }

        // 4. Use LLM for deeper analysis
        if (cactusLM != null && cactusLM.isLoaded()) {
            val llmPriority = analyzePriorityWithLLM(notification)
            if (llmPriority != null) {
                return llmPriority
            }
        }

        // Default to low priority
        return NotificationPriority.LOW
    }

    private fun containsPriorityKeywords(notification: NotificationData): Boolean {
        val text = "${notification.title} ${notification.text}".lowercase()
        return settings.priorityKeywords.any { keyword ->
            text.contains(keyword.lowercase())
        }
    }

    private fun isFromPriorityContact(notification: NotificationData): Boolean {
        // This would require extracting contact info from notification
        // For messaging apps, we'd need to parse the notification text
        // For now, return false - can be enhanced based on app type
        return false
    }

    private suspend fun analyzePriorityWithLLM(notification: NotificationData): NotificationPriority? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    Analyze this notification and determine if it's HIGH priority or LOW priority.

                    HIGH priority means: urgent, time-sensitive, important, requires immediate attention, from important people, emergencies, work-critical.
                    LOW priority means: promotional, informational, social media likes/comments, general updates, non-urgent.

                    Notification from: ${notification.packageName}
                    Title: ${notification.title}
                    Message: ${notification.text}

                    Respond with ONLY one word: HIGH or LOW
                """.trimIndent()

                val result = cactusLM?.generateCompletion(
                    messages = listOf(ChatMessage(content = prompt, role = "user"))
                )

                val response = result?.response?.trim()?.uppercase()
                when {
                    response?.contains("HIGH") == true -> NotificationPriority.HIGH
                    response?.contains("LOW") == true -> NotificationPriority.LOW
                    else -> null
                }
            } catch (e: Exception) {
                null // Fall back to default if LLM fails
            }
        }
    }
}
