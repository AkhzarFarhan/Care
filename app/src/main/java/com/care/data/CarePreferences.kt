package com.care.data

import android.content.Context
import java.util.UUID

class CarePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("care_preferences", Context.MODE_PRIVATE)

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoringEnabled", false)
        set(value) = prefs.edit().putBoolean("monitoringEnabled", value).apply()

    val pollingIntervalSeconds: Long = 10

    var initialPermissionFlowCompleted: Boolean
        get() = prefs.getBoolean("initialPermissionFlowCompleted", false)
        set(value) = prefs.edit().putBoolean("initialPermissionFlowCompleted", value).apply()

    var deviceName: String
        get() = prefs.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString("deviceName", value.ifBlank { android.os.Build.MODEL }).apply()

    var lastPermissionSignature: String?
        get() = prefs.getString("lastPermissionSignature", null)
        set(value) = prefs.edit().putString("lastPermissionSignature", value).apply()

    var deviceRegistrationCompleted: Boolean
        get() = prefs.getBoolean("deviceRegistrationCompleted", false)
        set(value) = prefs.edit().putBoolean("deviceRegistrationCompleted", value).apply()

    val deviceId: String
        get() {
            prefs.getString("deviceId", null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", generated).apply()
            return generated
        }
}
