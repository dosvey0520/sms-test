package com.citihardware.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the gateway after the phone reboots, if it was running before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.enabled(context)) {
            try {
                context.startForegroundService(Intent(context, SmsService::class.java))
            } catch (e: Exception) {
                LogStore.add("Auto-start after boot failed: ${e.message}")
            }
        }
    }
}
