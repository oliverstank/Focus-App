package com.cactus.example

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

class DumbModeViewModel : ViewModel() {

    private val _settings = MutableStateFlow(DumbModeSettings())
    val settings: StateFlow<DumbModeSettings> = _settings.asStateFlow()

    private val _installedApps = MutableStateFlow<List<WhitelistedAppInfo>>(emptyList())
    val installedApps: StateFlow<List<WhitelistedAppInfo>> = _installedApps.asStateFlow()

    private val _queuedNotifications = MutableStateFlow<List<QueuedNotification>>(emptyList())
    val queuedNotifications: StateFlow<List<QueuedNotification>> = _queuedNotifications.asStateFlow()

    init {
        viewModelScope.launch {
            DumbModeRepository.settings.collect { settings ->
                _settings.value = settings
            }
        }
        viewModelScope.launch {
            DumbModeRepository.queuedNotifications.collect { notifications ->
                _queuedNotifications.value = notifications
            }
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = context.packageManager

            // Get all apps that can be launched
            val apps = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Keep apps that have a launcher intent
                    packageManager.getLaunchIntentForPackage(appInfo.packageName) != null &&
                    appInfo.packageName != context.packageName
                }
                .map { appInfo ->
                    WhitelistedAppInfo(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        isWhitelisted = appInfo.packageName in _settings.value.whitelistedApps
                    )
                }
                .sortedBy { it.appName }

            _installedApps.value = apps
        }
    }

    fun toggleDumbMode(context: Context, onPermissionNeeded: (String) -> Unit) {
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

            DumbModeRepository.setDumbModeEnabled(true)

            // Start the app blocker service
            val intent = Intent(context, AppBlockerService::class.java)
            context.startForegroundService(intent)
        } else {
            DumbModeRepository.setDumbModeEnabled(false)

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
            DumbModeRepository.removeWhitelistedApp(packageName)
        } else {
            DumbModeRepository.addWhitelistedApp(packageName)
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
        DumbModeRepository.updateSettings(newSettings)
    }

    fun addPriorityKeyword(keyword: String) {
        val newSettings = _settings.value.copy(
            priorityKeywords = _settings.value.priorityKeywords + keyword
        )
        DumbModeRepository.updateSettings(newSettings)
    }

    fun removePriorityKeyword(keyword: String) {
        val newSettings = _settings.value.copy(
            priorityKeywords = _settings.value.priorityKeywords - keyword
        )
        DumbModeRepository.updateSettings(newSettings)
    }

    fun getTodayQueuedNotifications(): List<QueuedNotification> {
        return DumbModeRepository.getTodayQueuedNotifications()
    }

    fun clearQueuedNotifications() {
        DumbModeRepository.clearQueuedNotifications()
    }
}
