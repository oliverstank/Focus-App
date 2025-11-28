package com.cactus.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("package_name") ?: return

        // Add temporary unlock
        DumbModeRepository.addTemporaryUnlock(packageName)

        // Show confirmation
        val appName = packageName.split(".").lastOrNull() ?: packageName
        Toast.makeText(
            context,
            "Unlocked $appName for 5 minutes",
            Toast.LENGTH_LONG
        ).show()

        // Try to open the app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
}
