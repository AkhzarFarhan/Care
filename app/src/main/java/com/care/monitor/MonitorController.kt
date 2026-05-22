package com.care.monitor

import android.content.Context
import android.content.Intent
import android.os.Build
import com.care.data.CarePreferences

class MonitorController(
    private val context: Context,
    private val prefs: CarePreferences
) {
    fun start() {
        prefs.monitoringEnabled = true
        val intent = Intent(context, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop() {
        prefs.monitoringEnabled = false
        context.stopService(Intent(context, MonitorService::class.java))
    }
}
