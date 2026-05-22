package com.care.data

import android.content.Context
import android.provider.Settings
import java.util.UUID

class CarePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("care_preferences", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoringEnabled", false)
        set(value) = prefs.edit().putBoolean("monitoringEnabled", value).apply()

    var firebaseSyncEnabled: Boolean
        get() = prefs.getBoolean("firebaseSyncEnabled", true)
        set(value) = prefs.edit().putBoolean("firebaseSyncEnabled", value).apply()

    var browserTrackingEnabled: Boolean
        get() = prefs.getBoolean("browserTrackingEnabled", true)
        set(value) = prefs.edit().putBoolean("browserTrackingEnabled", value).apply()

    var pollingIntervalSeconds: Long
        get() = prefs.getLong("pollingIntervalSeconds", 10)
        set(value) = prefs.edit().putLong("pollingIntervalSeconds", value.coerceIn(5, 60)).apply()

    var parentUserId: String?
        get() = prefs.getString("parentUserId", null)
        set(value) = prefs.edit().putString("parentUserId", value).apply()

    var deviceName: String
        get() = prefs.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString("deviceName", value.ifBlank { android.os.Build.MODEL }).apply()

    val deviceId: String
        get() {
            prefs.getString("deviceId", null)?.let { return it }
            val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            val generated = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", generated).apply()
            return generated
        }
}
