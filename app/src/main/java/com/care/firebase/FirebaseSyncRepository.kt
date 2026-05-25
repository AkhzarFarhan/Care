package com.care.firebase

import com.care.data.ActivityEvent
import com.care.data.ActivityEventRepository
import com.care.data.CarePreferences
import com.care.data.EventType
import com.care.util.PermissionState
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.lang.Integer.toUnsignedString

data class SyncResult(
    val attempted: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

class FirebaseSyncRepository(
    private val eventRepository: ActivityEventRepository,
    private val prefs: CarePreferences
) {
    private val deviceReference: DatabaseReference
        get() = FirebaseDatabase.getInstance()
            .getReference(CARE_NODE)
            .child(prefs.deviceId)

    suspend fun registerDevice(): Result<String> = runCatching {
        deviceReference.child("device").updateChildren(
            mapOf(
                "deviceId" to prefs.deviceId,
                "deviceName" to prefs.deviceName,
                "model" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "lastSeenAt" to System.currentTimeMillis(),
                "syncIntervalMillis" to SYNC_INTERVAL_MS,
                "localBufferMillis" to LOCAL_BUFFER_MS
            )
        ).await()
        prefs.deviceRegistrationCompleted = true
        prefs.deviceId
    }

    suspend fun reportPermissionState(state: PermissionState): Result<Unit> = runCatching {
        val signature = state.signature()
        if (prefs.lastPermissionSignature != signature) {
            eventRepository.record(
                eventType = EventType.PERMISSION_STATUS,
                details = signature
            )
            prefs.lastPermissionSignature = signature
        }
    }

    suspend fun syncPending(limit: Int = DEFAULT_BATCH_LIMIT): SyncResult {
        registerDevice().getOrThrow()
        val uploadBefore = System.currentTimeMillis() - LOCAL_BUFFER_MS
        val events = eventRepository.pendingSyncEventsBefore(uploadBefore, limit)
        if (events.isEmpty()) {
            return SyncResult(message = "Captured events are queued for the next five-minute upload window.")
        }

        var succeeded = 0
        var failed = 0
        var firstFailure: Exception? = null
        val batches = events.groupBy { it.eventType }

        for ((eventType, batchEvents) in batches) {
            try {
                val cloudType = eventType.cloudPath()
                val batchId = batchEvents.batchId()
                deviceReference.child("events")
                    .child(cloudType)
                    .child(batchId)
                    .setValue(batchEvents.toAggregatedBatch(eventType, batchId))
                    .await()

                if (eventType == EventType.PERMISSION_STATUS) {
                    batchEvents.latestPermissionSnapshot()?.let { snapshot ->
                        deviceReference.child("permissionStatus")
                            .child("current")
                            .setValue(snapshot)
                            .await()
                    }
                }

                eventRepository.markSynced(batchEvents.map { it.id })
                succeeded += batchEvents.size
            } catch (error: Exception) {
                eventRepository.markFailed(batchEvents.map { it.id })
                failed += batchEvents.size
                if (firstFailure == null) firstFailure = error
            }
        }

        return SyncResult(
            attempted = events.size,
            succeeded = succeeded,
            failed = failed,
            message = firstFailure?.let { error ->
                "Uploaded $succeeded of ${events.size} queued events. Upload failed: ${error.message ?: error.javaClass.simpleName}"
            } ?: "Uploaded $succeeded queued events in ${batches.size} aggregated batches."
        )
    }

    private fun List<ActivityEvent>.toAggregatedBatch(eventType: EventType, batchId: String): Map<String, Any?> {
        val batch = linkedMapOf<String, Any?>(
            "deviceId" to prefs.deviceId,
            "batchId" to batchId,
            "eventType" to eventType.cloudPath(),
            "sourceEventType" to eventType.name,
            "eventCount" to size,
            "firstEventAt" to minOf { it.timestamp },
            "lastEventAt" to maxOf { it.timestamp },
            "firstRecordedAt" to minOf { it.createdAt },
            "lastRecordedAt" to maxOf { it.createdAt },
            "uploadedAt" to System.currentTimeMillis()
        )

        batch.putAll(
            when (eventType) {
                EventType.APP_USAGE -> appUsageAggregation()
                EventType.URL -> urlAggregation()
                EventType.PERMISSION_STATUS -> permissionAggregation()
                EventType.SCREEN_ON,
                EventType.SCREEN_OFF,
                EventType.UNLOCK -> deviceStateAggregation()
            }
        )

        return batch
    }

    private fun List<ActivityEvent>.appUsageAggregation(): Map<String, Any> {
        val apps = linkedMapOf<String, Map<String, Any>>()
        var totalDurationMs = 0L

        groupBy { it.packageName ?: "unknown" }.forEach { (packageName, appEvents) ->
            val durationMs = appEvents.sumOf { it.durationMs() }
            totalDurationMs += durationMs
            apps[safeKey(packageName)] = mapOf(
                "packageName" to packageName,
                "sessionCount" to appEvents.size,
                "totalDurationMs" to durationMs,
                "totalDurationSeconds" to durationMs / 1000,
                "firstStartAt" to appEvents.minOf { it.startTimestamp ?: it.timestamp },
                "lastEndAt" to appEvents.maxOf { it.endTimestamp ?: it.timestamp }
            )
        }

        return mapOf(
            "appCount" to apps.size,
            "sessionCount" to size,
            "totalDurationMs" to totalDurationMs,
            "totalDurationSeconds" to totalDurationMs / 1000,
            "apps" to apps
        )
    }

    private fun List<ActivityEvent>.urlAggregation(): Map<String, Any> {
        val visits = linkedMapOf<String, Map<String, Any>>()

        groupBy { "${it.packageName ?: "unknown"}|${it.details ?: "unknown"}" }
            .forEach { (key, urlEvents) ->
                val first = urlEvents.first()
                visits[safeKey(key)] = mapOf(
                    "packageName" to (first.packageName ?: "unknown"),
                    "url" to (first.details ?: "unknown"),
                    "visitCount" to urlEvents.size,
                    "firstSeenAt" to urlEvents.minOf { it.timestamp },
                    "lastSeenAt" to urlEvents.maxOf { it.timestamp }
                )
            }

        return mapOf(
            "uniqueUrlCount" to visits.size,
            "visitCount" to size,
            "visits" to visits
        )
    }

    private fun List<ActivityEvent>.deviceStateAggregation(): Map<String, Any> {
        val actions = linkedMapOf<String, Map<String, Any>>()

        groupBy { it.details ?: it.eventType.name }.forEach { (action, actionEvents) ->
            actions[safeKey(action)] = mapOf(
                "action" to action,
                "count" to actionEvents.size,
                "firstSeenAt" to actionEvents.minOf { it.timestamp },
                "lastSeenAt" to actionEvents.maxOf { it.timestamp }
            )
        }

        return mapOf(
            "count" to size,
            "actions" to actions
        )
    }

    private fun List<ActivityEvent>.permissionAggregation(): Map<String, Any> {
        val states = linkedMapOf<String, Map<String, Any>>()

        groupBy { it.details ?: "unknown" }.forEach { (signature, stateEvents) ->
            states[safeKey(signature)] = mapOf(
                "signature" to signature,
                "count" to stateEvents.size,
                "firstSeenAt" to stateEvents.minOf { it.timestamp },
                "lastSeenAt" to stateEvents.maxOf { it.timestamp }
            )
        }

        return mapOf(
            "changeCount" to size,
            "uniqueStateCount" to states.size,
            "latestSignature" to (maxByOrNull { it.timestamp }?.details ?: "unknown"),
            "states" to states
        )
    }

    private fun List<ActivityEvent>.latestPermissionSnapshot(): Map<String, Any?>? {
        val latest = maxByOrNull { it.timestamp } ?: return null
        val flags = latest.details.parsePermissionFlags()
        return linkedMapOf(
            "deviceId" to prefs.deviceId,
            "signature" to latest.details,
            "usageAccessGranted" to flags["usageAccessGranted"],
            "accessibilityGranted" to flags["accessibilityGranted"],
            "notificationGranted" to flags["notificationGranted"],
            "batteryOptimizationIgnored" to flags["batteryOptimizationIgnored"],
            "allRequiredGranted" to (flags.isNotEmpty() && flags.values.all { it }),
            "updatedAt" to latest.timestamp,
            "uploadedAt" to System.currentTimeMillis()
        ).filterValues { it != null }
    }

    private fun String?.parsePermissionFlags(): Map<String, Boolean> {
        if (isNullOrBlank()) return emptyMap()
        val parsed = split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1].toBooleanStrictOrNull() else null
            }
            .toMap()
        return mapOf(
            "usageAccessGranted" to (parsed["usageAccess"] == true),
            "accessibilityGranted" to (parsed["accessibility"] == true),
            "notificationGranted" to (parsed["notifications"] == true),
            "batteryOptimizationIgnored" to (parsed["batteryExempt"] == true)
        )
    }

    private fun PermissionState.signature(): String =
        "usageAccess=$usageAccessGranted;accessibility=$accessibilityGranted;" +
            "notifications=$notificationGranted;batteryExempt=$batteryOptimizationIgnored"

    private fun List<ActivityEvent>.batchId(): String =
        "${minOf { it.createdAt }}_${maxOf { it.createdAt }}"

    private fun ActivityEvent.durationMs(): Long =
        ((endTimestamp ?: timestamp) - (startTimestamp ?: timestamp)).coerceAtLeast(0L)

    private fun EventType.cloudPath(): String = when (this) {
        EventType.APP_USAGE -> "AppUsage"
        EventType.URL -> "Url"
        EventType.SCREEN_ON -> "ScreenOn"
        EventType.SCREEN_OFF -> "ScreenOff"
        EventType.UNLOCK -> "Unlock"
        EventType.PERMISSION_STATUS -> "PermissionStatus"
    }

    private fun safeKey(value: String): String =
        "k_${toUnsignedString(value.hashCode(), 16)}"

    companion object {
        const val SYNC_INTERVAL_MS = 5 * 60 * 1000L
        private const val LOCAL_BUFFER_MS = 5 * 60 * 1000L
        private const val DEFAULT_BATCH_LIMIT = 1000
        private const val CARE_NODE = "Care"
    }
}
