package com.care.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.data.CarePreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = CarePreferences(context)
        if (prefs.monitoringEnabled) {
            MonitorController(context.applicationContext, prefs).start()
        }
    }
}
