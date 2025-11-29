package com.cactus.example

data class FocusModeSettings(
    val isEnabled: Boolean = false,
    val whitelistedApps: Set<String> = EssentialApps.DEFAULT_WHITELIST, // Apps that are allowed
    val priorityContacts: Set<String> = emptySet(), // Phone numbers or contact IDs
    val priorityKeywords: Set<String> = setOf(
        "urgent", "emergency", "asap", "important", "critical",
        "911", "help", "SOS", "alert", "warning"
    ),
    val summaryTime: SummaryTime = SummaryTime(20, 0), // 8:00 PM
    val alwaysPriorityApps: Set<String> = emptySet(), // Apps that are always priority
    val neverPriorityApps: Set<String> = emptySet() // Apps that are never priority
)

// Essential apps that should be whitelisted by default
object EssentialApps {
    val DEFAULT_WHITELIST = setOf(
        // System & Settings
        "com.android.settings",
        "com.google.android.settings",
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",

        // Phone & Emergency
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.emergency",

        // Contacts
        "com.android.contacts",
        "com.google.android.contacts",

        // Messages (SMS)
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.mms",

        // Email
        "com.android.email",
        "com.google.android.gm", // Gmail

        // Camera & Photos
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.google.android.apps.photos",
        "com.android.gallery3d",

        // Maps & Navigation
        "com.google.android.apps.maps",
        "com.android.vending", // Play Store (for updates)

        // Calendar & Clock
        "com.android.calendar",
        "com.google.android.calendar",
        "com.android.deskclock",
        "com.google.android.deskclock",

        // Files & Documents
        "com.android.documentsui",
        "com.google.android.apps.docs",

        // Health & Fitness
        "com.google.android.apps.fitness",

        // Browser (minimal - for important lookups)
        "com.android.chrome",
        "com.google.android.apps.chrome"
    )

    fun isEssentialApp(packageName: String): Boolean {
        return packageName in DEFAULT_WHITELIST ||
               packageName.startsWith("com.android.") ||
               packageName.contains("emergency") ||
               packageName.contains("dialer") ||
               packageName.contains("phone")
    }
}

data class SummaryTime(
    val hour: Int, // 0-23
    val minute: Int // 0-59
)

data class WhitelistedAppInfo(
    val packageName: String,
    val appName: String,
    val isWhitelisted: Boolean
)

data class TemporaryUnlock(
    val packageName: String,
    val unlockTime: Long, // Timestamp when unlocked
    val durationMinutes: Int = 5
) {
    fun isStillUnlocked(): Boolean {
        val currentTime = System.currentTimeMillis()
        val unlockDuration = durationMinutes * 60 * 1000L
        return (currentTime - unlockTime) < unlockDuration
    }

    fun remainingSeconds(): Long {
        val currentTime = System.currentTimeMillis()
        val unlockDuration = durationMinutes * 60 * 1000L
        val elapsed = currentTime - unlockTime
        return ((unlockDuration - elapsed) / 1000).coerceAtLeast(0)
    }
}

enum class NotificationPriority {
    HIGH,    // Show immediately and allow app unlock
    LOW      // Queue for end-of-day summary
}

data class QueuedNotification(
    val notification: NotificationData,
    val queuedAt: Long = System.currentTimeMillis(),
    val priority: NotificationPriority = NotificationPriority.LOW
)
