package com.care.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityEventRepository(
    private val dao: ActivityEventDao,
    private val prefs: CarePreferences
) {
    fun recentEvents(limit: Int = 100): Flow<List<ActivityEvent>> = dao.recentEvents(limit)

    fun todayEvents(): Flow<List<ActivityEvent>> = dao.eventsSince(startOfToday())

    fun pendingSyncCount(): Flow<Int> = dao.pendingSyncCount()

    suspend fun record(
        eventType: EventType,
        packageName: String? = null,
        details: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        startTimestamp: Long? = null,
        endTimestamp: Long? = null
    ) {
        dao.insert(
            ActivityEvent(
                eventType = eventType,
                packageName = packageName,
                details = details,
                timestamp = timestamp,
                startTimestamp = startTimestamp,
                endTimestamp = endTimestamp,
                deviceId = prefs.deviceId
            )
        )
    }

    suspend fun pendingSyncEvents(limit: Int = 100): List<ActivityEvent> = dao.pendingSyncEvents(limit)

    suspend fun markSynced(id: Long) = dao.updateSyncState(id, SyncStatus.SYNCED, System.currentTimeMillis())

    suspend fun markFailed(id: Long) = dao.updateSyncState(id, SyncStatus.FAILED, null)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun exportCsv(from: Long, to: Long): String {
        val rows = dao.eventsBetween(from, to)
        return buildString {
            appendLine("id,eventType,packageName,details,timestamp,startTimestamp,endTimestamp,deviceId,syncStatus,createdAt,syncedAt")
            rows.forEach { event ->
                appendLine(
                    listOf(
                        event.id,
                        event.eventType.name,
                        event.packageName.orEmpty(),
                        event.details.orEmpty(),
                        event.timestamp,
                        event.startTimestamp ?: "",
                        event.endTimestamp ?: "",
                        event.deviceId,
                        event.syncStatus.name,
                        event.createdAt,
                        event.syncedAt ?: ""
                    ).joinToString(",") { csvEscape(it.toString()) }
                )
            }
        }
    }

    suspend fun exportJson(from: Long, to: Long): String {
        val rows = dao.eventsBetween(from, to)
        return rows.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { event ->
            """
            {
              "id": ${event.id},
              "eventType": "${event.eventType.name}",
              "packageName": ${event.packageName.jsonOrNull()},
              "details": ${event.details.jsonOrNull()},
              "timestamp": ${event.timestamp},
              "startTimestamp": ${event.startTimestamp ?: "null"},
              "endTimestamp": ${event.endTimestamp ?: "null"},
              "deviceId": "${event.deviceId}",
              "syncStatus": "${event.syncStatus.name}",
              "createdAt": ${event.createdAt},
              "syncedAt": ${event.syncedAt ?: "null"}
            }
            """.trimIndent()
        }
    }

    private fun startOfToday(): Long {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.parse(formatter.format(Date()))?.time ?: System.currentTimeMillis()
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun String?.jsonOrNull(): String =
        this?.let { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" } ?: "null"
}
