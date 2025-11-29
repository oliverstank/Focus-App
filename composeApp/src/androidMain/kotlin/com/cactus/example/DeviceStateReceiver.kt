package com.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DeviceStateReceiver", "Received action: ${intent.action}")
        val serviceIntent = Intent(context, AppBlockerService::class.java)
        serviceIntent.action = "com.focus.ACTION_CHECK_FOREGROUND_APP"
        context.startService(serviceIntent)
    }
}
