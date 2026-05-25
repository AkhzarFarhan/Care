package com.care.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.care.R
import com.care.data.EventType
import com.care.data.ServiceLocator
import com.care.firebase.FirebaseSyncRepository
import com.care.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var usageJob: Job? = null
    private var syncJob: Job? = null
    private var receiver: DeviceStateReceiver? = null
    private var lastUsageQueryTime = System.currentTimeMillis()
    private val activeApps = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerDeviceReceiver()
        startUsagePolling()
        startSyncLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLocator.prefs.monitoringEnabled = true
        return START_STICKY
    }

    override fun onDestroy() {
        usageJob?.cancel()
        syncJob?.cancel()
        receiver?.let { unregisterReceiver(it) }
        ServiceLocator.prefs.monitoringEnabled = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerDeviceReceiver() {
        receiver = DeviceStateReceiver()
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )
    }

    private fun startUsagePolling() {
        usageJob = scope.launch {
            while (isActive) {
                pollUsageEvents()
                delay(ServiceLocator.prefs.pollingIntervalSeconds * 1000)
            }
        }
    }

    private suspend fun pollUsageEvents() {
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(lastUsageQueryTime, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> activeApps[packageName] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = activeApps.remove(packageName) ?: continue
                    ServiceLocator.eventRepository.record(
                        eventType = EventType.APP_USAGE,
                        packageName = packageName,
                        details = "App used for ${(event.timeStamp - start).coerceAtLeast(0) / 1000}s",
                        timestamp = event.timeStamp,
                        startTimestamp = start,
                        endTimestamp = event.timeStamp
                    )
                }
            }
        }

        lastUsageQueryTime = now
    }

    private fun startSyncLoop() {
        syncJob = scope.launch {
            while (isActive) {
                delay(FirebaseSyncRepository.SYNC_INTERVAL_MS)
                runCatching {
                    ServiceLocator.firebaseSyncRepository.reportPermissionState(
                        PermissionUtils.state(this@MonitorService)
                    ).getOrThrow()
                }.onFailure { error ->
                    Log.w(TAG, "Permission-state upload failed.", error)
                }
                runCatching {
                    ServiceLocator.firebaseSyncRepository.syncPending()
                }.onSuccess { result ->
                    if (result.failed > 0) Log.w(TAG, result.message)
                }.onFailure { error ->
                    Log.w(TAG, "Activity upload initialization failed.", error)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Care monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "CareSync"
        private const val CHANNEL_ID = "care_monitoring"
        private const val NOTIFICATION_ID = 42
    }
}
