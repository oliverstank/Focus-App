package com.focus

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusModeViewModel : ViewModel() {

    private val _settings = MutableStateFlow(FocusModeSettings())
    val settings: StateFlow<FocusModeSettings> = _settings.asStateFlow()

    private val _installedApps = MutableStateFlow<List<WhitelistedAppInfo>>(emptyList())
    val installedApps: StateFlow<List<WhitelistedAppInfo>> = _installedApps.asStateFlow()

    private val _queuedNotifications = MutableStateFlow<List<QueuedNotification>>(emptyList())
    val queuedNotifications: StateFlow<List<QueuedNotification>> = _queuedNotifications.asStateFlow()

    private val _importantNotifications = MutableStateFlow<List<CategorizedNotification>>(emptyList())
    val importantNotifications: StateFlow<List<CategorizedNotification>> = _importantNotifications.asStateFlow()

    private val _unimportantNotifications = MutableStateFlow<List<CategorizedNotification>>(emptyList())
    val unimportantNotifications: StateFlow<List<CategorizedNotification>> = _unimportantNotifications.asStateFlow()

    init {
        viewModelScope.launch {
            FocusModeRepository.settings.collect { settings ->
                _settings.value = settings
            }
        }
        viewModelScope.launch {
            FocusModeRepository.queuedNotifications.collect { notifications ->
                _queuedNotifications.value = notifications
            }
        }
        viewModelScope.launch {
            FocusModeRepository.importantNotifications.collect { notifications ->
                _importantNotifications.value = notifications
            }
        }
        viewModelScope.launch {
            FocusModeRepository.unimportantNotifications.collect { notifications ->
                _unimportantNotifications.value = notifications
            }
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = context.packageManager

            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

            val apps = packageManager.queryIntentActivities(mainIntent, 0)
                .map { resolveInfo ->
                    WhitelistedAppInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(packageManager).toString(),
                        isWhitelisted = resolveInfo.activityInfo.packageName in _settings.value.whitelistedApps
                    )
                }
                .filter { appInfo ->
                    appInfo.packageName != context.packageName
                }
                .sortedBy { it.appName }

            _installedApps.value = apps
        }
    }

    fun toggleFocusMode(context: Context, onPermissionNeeded: (String) -> Unit) {
        val newEnabled = !_settings.value.isEnabled

        if (newEnabled) {
            // Check for overlay permission first
            if (!android.provider.Settings.canDrawOverlays(context)) {
                onPermissionNeeded("overlay")
                return
            }

            // Check for usage stats permission
            if (!hasUsageStatsPermission(context)) {
                onPermissionNeeded("usage_stats")
                return
            }

            FocusModeRepository.setFocusModeEnabled(true)

            // Start the app blocker service
            val intent = Intent(context, AppBlockerService::class.java)
            context.startForegroundService(intent)
        } else {
            FocusModeRepository.setFocusModeEnabled(false)

            // Stop the app blocker service
            val intent = Intent(context, AppBlockerService::class.java)
            context.stopService(intent)

            // Show end-of-day summary if there are queued notifications
            if (_queuedNotifications.value.isNotEmpty()) {
                // This would trigger showing the summary screen
            }
        }
    }
    
    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOpsManager.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun toggleAppWhitelisted(packageName: String) {
        if (packageName in _settings.value.whitelistedApps) {
            FocusModeRepository.removeWhitelistedApp(packageName)
        } else {
            FocusModeRepository.addWhitelistedApp(packageName)
        }

        // Update the local list
        _installedApps.value = _installedApps.value.map { app ->
            if (app.packageName == packageName) {
                app.copy(isWhitelisted = !app.isWhitelisted)
            } else {
                app
            }
        }
    }

    fun updateSummaryTime(hour: Int, minute: Int) {
        val newSettings = _settings.value.copy(
            summaryTime = SummaryTime(hour, minute)
        )
        FocusModeRepository.updateSettings(newSettings)
    }

    fun addPriorityKeyword(keyword: String) {
        val newSettings = _settings.value.copy(
            priorityKeywords = _settings.value.priorityKeywords + keyword
        )
        FocusModeRepository.updateSettings(newSettings)
    }

    fun removePriorityKeyword(keyword: String) {
        val newSettings = _settings.value.copy(
            priorityKeywords = _settings.value.priorityKeywords - keyword
        )
        FocusModeRepository.updateSettings(newSettings)
    }

    fun getTodayQueuedNotifications(): List<QueuedNotification> {
        return FocusModeRepository.getTodayQueuedNotifications()
    }

    fun clearQueuedNotifications() {
        FocusModeRepository.clearQueuedNotifications()
    }

    fun getUnimportantNotificationCount(): Int {
        return FocusModeRepository.getUnimportantNotificationCount()
    }

    fun clearCategorizedNotifications() {
        FocusModeRepository.clearCategorizedNotifications()
    }
}