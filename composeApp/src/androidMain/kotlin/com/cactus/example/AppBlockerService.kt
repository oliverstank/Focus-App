package com.cactus.example

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppBlockerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var blockingView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var currentBlockedApp: String? = null

    override fun onCreate() {
        super.onCreate()
        DumbModeRepository.initialize(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Start monitoring
        startMonitoring()

        // Observe settings changes
        serviceScope.launch {
            DumbModeRepository.settings.collect { settings ->
                if (!settings.isEnabled) {
                    stopSelf()
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dumb Mode Active",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring and blocking apps"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dumb Mode Active")
            .setContentText("Non-essential apps are blocked")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        checkRunnable = object : Runnable {
            override fun run() {
                checkForegroundApp()
                handler.postDelayed(this, 500) // Check every 500ms
            }
        }
        handler.post(checkRunnable!!)
    }

    private fun checkForegroundApp() {
        val foregroundPackage = getForegroundApp() ?: return

        // Check if app should be blocked
        if (DumbModeRepository.isAppBlocked(foregroundPackage)) {
            if (currentBlockedApp != foregroundPackage) {
                currentBlockedApp = foregroundPackage
                showBlockingOverlay(foregroundPackage)
            }
        } else {
            removeBlockingOverlay()
            currentBlockedApp = null
        }
    }

    private fun getForegroundApp(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val currentTime = System.currentTimeMillis()

            // Query usage stats for the last second
            val stats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 1000,
                currentTime
            )

            // Get the most recently used app
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    private fun showBlockingOverlay(packageName: String) {
        if (blockingView != null) return

        try {
            val appName = packageName.split(".").lastOrNull() ?: packageName
            val unlockInfo = DumbModeRepository.temporaryUnlocks.value
                .firstOrNull { it.packageName == packageName && it.isStillUnlocked() }

            blockingView = createBlockingView(appName, unlockInfo)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager?.addView(blockingView, layoutParams)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createBlockingView(appName: String, unlockInfo: TemporaryUnlock?): android.view.View {
        // Create a simple blocking view programmatically
        return android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#000000"))
            setPadding(48, 48, 48, 48)

            addView(android.widget.TextView(this@AppBlockerService).apply {
                text = "🔒"
                textSize = 72f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            })

            addView(android.widget.TextView(this@AppBlockerService).apply {
                text = "App Blocked"
                textSize = 28f
                setTextColor(android.graphics.Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })

            addView(android.widget.TextView(this@AppBlockerService).apply {
                text = "\"$appName\" is not allowed in Dumb Mode"
                textSize = 18f
                setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            })

            if (unlockInfo != null) {
                val remaining = unlockInfo.remainingSeconds()
                addView(android.widget.TextView(this@AppBlockerService).apply {
                    text = "Unlocked for ${remaining}s"
                    textSize = 16f
                    setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 32)
                })
            }

            // Add OK button
            addView(android.widget.Button(this@AppBlockerService).apply {
                text = "OK"
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                setTextColor(android.graphics.Color.parseColor("#000000"))
                setPadding(64, 24, 64, 24)

                setOnClickListener {
                    // Remove overlay
                    removeBlockingOverlay()

                    // Return to home
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
            })
        }
    }

    private fun removeBlockingOverlay() {
        blockingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            blockingView = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        checkRunnable?.let { handler.removeCallbacks(it) }
        removeBlockingOverlay()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "app_blocker_channel"
    }
}
