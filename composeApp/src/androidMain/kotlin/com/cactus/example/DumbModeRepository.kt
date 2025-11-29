package com.focus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FocusModeRepository {
    private lateinit var prefs: SharedPreferences
    private var appPackageName: String = ""

    private val _settings = MutableStateFlow(FocusModeSettings())
    val settings: StateFlow<FocusModeSettings> = _settings.asStateFlow()

    private val _temporaryUnlocks = MutableStateFlow<List<TemporaryUnlock>>(emptyList())
    val temporaryUnlocks: StateFlow<List<TemporaryUnlock>> = _temporaryUnlocks.asStateFlow()

    private val _queuedNotifications = MutableStateFlow<List<QueuedNotification>>(emptyList())
    val queuedNotifications: StateFlow<List<QueuedNotification>> = _queuedNotifications.asStateFlow()

    // Pending notifications waiting for batch processing
    private val _pendingNotifications = MutableStateFlow<List<NotificationData>>(emptyList())
    val pendingNotifications: StateFlow<List<NotificationData>> = _pendingNotifications.asStateFlow()

    // Categorized notifications (important and unimportant)
    private val _importantNotifications = MutableStateFlow<List<CategorizedNotification>>(emptyList())
    val importantNotifications: StateFlow<List<CategorizedNotification>> = _importantNotifications.asStateFlow()

    private val _unimportantNotifications = MutableStateFlow<List<CategorizedNotification>>(emptyList())
    val unimportantNotifications: StateFlow<List<CategorizedNotification>> = _unimportantNotifications.asStateFlow()

    // Processing status tracking
    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("focus_mode_prefs", Context.MODE_PRIVATE)
        appPackageName = context.packageName
        loadSettings()
    }

    private fun loadSettings() {
        val isEnabled = prefs.getBoolean("is_enabled", false)
        val whitelistedApps = prefs.getStringSet("whitelisted_apps", null) ?: EssentialApps.DEFAULT_WHITELIST
        val priorityContacts = prefs.getStringSet("priority_contacts", emptySet()) ?: emptySet()
        val priorityKeywords = prefs.getStringSet("priority_keywords", null) ?: FocusModeSettings().priorityKeywords
        val summaryHour = prefs.getInt("summary_hour", 20)
        val summaryMinute = prefs.getInt("summary_minute", 0)
        val alwaysPriorityApps = prefs.getStringSet("always_priority_apps", emptySet()) ?: emptySet()
        val neverPriorityApps = prefs.getStringSet("never_priority_apps", emptySet()) ?: emptySet()

        _settings.value = FocusModeSettings(
            isEnabled = isEnabled,
            whitelistedApps = whitelistedApps,
            priorityContacts = priorityContacts,
            priorityKeywords = priorityKeywords,
            summaryTime = SummaryTime(summaryHour, summaryMinute),
            alwaysPriorityApps = alwaysPriorityApps,
            neverPriorityApps = neverPriorityApps
        )
    }

    fun updateSettings(settings: FocusModeSettings) {
        prefs.edit().apply {
            putBoolean("is_enabled", settings.isEnabled)
            putStringSet("whitelisted_apps", settings.whitelistedApps)
            putStringSet("priority_contacts", settings.priorityContacts)
            putStringSet("priority_keywords", settings.priorityKeywords)
            putInt("summary_hour", settings.summaryTime.hour)
            putInt("summary_minute", settings.summaryTime.minute)
            putStringSet("always_priority_apps", settings.alwaysPriorityApps)
            putStringSet("never_priority_apps", settings.neverPriorityApps)
            apply()
        }
        _settings.value = settings
    }
    
    fun setFocusModeEnabled(enabled: Boolean) {
        val newSettings = _settings.value.copy(isEnabled = enabled)
        updateSettings(newSettings)
    }

    fun addWhitelistedApp(packageName: String) {
        val newSettings = _settings.value.copy(
            whitelistedApps = _settings.value.whitelistedApps + packageName
        )
        updateSettings(newSettings)
    }

    fun removeWhitelistedApp(packageName: String) {
        val newSettings = _settings.value.copy(
            whitelistedApps = _settings.value.whitelistedApps - packageName
        )
        updateSettings(newSettings)
    }

    fun addTemporaryUnlock(packageName: String) {
        val unlock = TemporaryUnlock(
            packageName = packageName,
            unlockTime = System.currentTimeMillis(),
            durationMinutes = 5
        )
        _temporaryUnlocks.value = _temporaryUnlocks.value + unlock
    }

    fun isAppTemporarilyUnlocked(packageName: String): Boolean {
        // Clean up expired unlocks
        _temporaryUnlocks.value = _temporaryUnlocks.value.filter { it.isStillUnlocked() }

        return _temporaryUnlocks.value.any {
            it.packageName == packageName && it.isStillUnlocked()
        }
    }

    fun isAppBlocked(packageName: String): Boolean {
        if (!_settings.value.isEnabled) return false
        // Never block the app itself
        if (packageName == appPackageName) return false
        // Always allow the launcher
        if (packageName == "com.google.android.apps.nexuslauncher") return false
        if (isAppTemporarilyUnlocked(packageName)) return false
        // Block all apps EXCEPT whitelisted ones
        val whitelistedApps = prefs.getStringSet("whitelisted_apps", null) ?: EssentialApps.DEFAULT_WHITELIST
        return packageName !in whitelistedApps
    }

    fun addQueuedNotification(notification: NotificationData, priority: NotificationPriority) {
        val queued = QueuedNotification(
            notification = notification,
            priority = priority
        )
        _queuedNotifications.value = _queuedNotifications.value + queued
    }

    fun getTodayQueuedNotifications(): List<QueuedNotification> {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        return _queuedNotifications.value.filter { it.queuedAt >= startOfDay }
    }

    fun clearQueuedNotifications() {
        _queuedNotifications.value = emptyList()
    }

    // Add a notification to the pending queue (for batch processing)
    fun addPendingNotification(notification: NotificationData) {
        _pendingNotifications.value = _pendingNotifications.value + notification
    }

    // Get and clear all pending notifications (for batch processing)
    fun getPendingNotificationsAndClear(): List<NotificationData> {
        val pending = _pendingNotifications.value
        _pendingNotifications.value = emptyList()
        return pending
    }

    // Clear pending notifications
    fun clearPendingNotifications() {
        _pendingNotifications.value = emptyList()
    }

    // Add important notification
    fun addImportantNotification(notification: NotificationData) {
        val categorized = CategorizedNotification(
            notification = notification,
            isImportant = true
        )
        _importantNotifications.value = _importantNotifications.value + categorized
    }

    // Add unimportant notification
    fun addUnimportantNotification(notification: NotificationData) {
        val categorized = CategorizedNotification(
            notification = notification,
            isImportant = false
        )
        _unimportantNotifications.value = _unimportantNotifications.value + categorized
    }

    // Get count of unimportant notifications
    fun getUnimportantNotificationCount(): Int {
        return _unimportantNotifications.value.size
    }

    // Clear all categorized notifications
    fun clearCategorizedNotifications() {
        _importantNotifications.value = emptyList()
        _unimportantNotifications.value = emptyList()
    }

    // Remove a specific important notification
    fun removeImportantNotification(notificationId: String) {
        _importantNotifications.value = _importantNotifications.value.filter {
            it.notification.id != notificationId
        }
    }

    // Remove a specific unimportant notification
    fun removeUnimportantNotification(notificationId: String) {
        _unimportantNotifications.value = _unimportantNotifications.value.filter {
            it.notification.id != notificationId
        }
    }

    // Get all notifications (for re-evaluation)
    fun getAllCategorizedNotifications(): List<NotificationData> {
        return (_importantNotifications.value.map { it.notification } +
                _unimportantNotifications.value.map { it.notification })
    }

    // Re-categorize all notifications (used when LLM re-evaluates)
    fun recategorizeNotifications(
        importantNotifications: List<NotificationData>,
        unimportantNotifications: List<NotificationData>
    ) {
        _importantNotifications.value = importantNotifications.map {
            CategorizedNotification(notification = it, isImportant = true)
        }
        _unimportantNotifications.value = unimportantNotifications.map {
            CategorizedNotification(notification = it, isImportant = false)
        }
    }

    // Update processing status
    fun updateProcessingStatus(status: ProcessingStatus, message: String = "") {
        val statusMessage = when (status) {
            ProcessingStatus.IDLE -> message.ifEmpty { "Waiting for notifications..." }
            ProcessingStatus.SCANNING -> message.ifEmpty { "Scanning notifications..." }
            ProcessingStatus.FILTERING -> message.ifEmpty { "Filtering notifications..." }
            ProcessingStatus.ANALYZING_WITH_LLM -> message.ifEmpty { "Analyzing with AI..." }
            ProcessingStatus.COMPLETE -> message.ifEmpty { "Processing complete" }
        }

        val currentTime = System.currentTimeMillis()
        val nextScan = if (status == ProcessingStatus.COMPLETE) {
            currentTime + (3 * 60 * 1000) // 3 minutes from now
        } else {
            _processingState.value.nextScanTime
        }

        _processingState.value = ProcessingState(
            status = status,
            message = statusMessage,
            lastProcessedTime = if (status == ProcessingStatus.COMPLETE) currentTime else _processingState.value.lastProcessedTime,
            nextScanTime = nextScan
        )
    }

    // Set next scan time
    fun setNextScanTime(timeMillis: Long) {
        _processingState.value = _processingState.value.copy(nextScanTime = timeMillis)
    }
}
