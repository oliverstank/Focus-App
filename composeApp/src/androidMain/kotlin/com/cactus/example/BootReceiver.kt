package com.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking focus mode status")

            val prefs = context.getSharedPreferences("focus_mode_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("is_enabled", false)

            if (isEnabled) {
                Log.d("BootReceiver", "Focus mode is enabled, starting AppBlockerService")
                val serviceIntent = Intent(context, AppBlockerService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.d("BootReceiver", "Focus mode is disabled, not starting service")
            }
        }
    }
}
