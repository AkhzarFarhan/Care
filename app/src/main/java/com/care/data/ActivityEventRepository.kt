package com.care.data

import kotlinx.coroutines.flow.Flow

class ActivityEventRepository(
    private val dao: ActivityEventDao,
    private val prefs: CarePreferences
) {
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

    suspend fun pendingSyncEventsBefore(beforeCreatedAt: Long, limit: Int = 1000): List<ActivityEvent> =
        dao.pendingSyncEventsBefore(beforeCreatedAt, limit)

    suspend fun markSynced(id: Long) = dao.updateSyncState(id, SyncStatus.SYNCED, System.currentTimeMillis())

    suspend fun markSynced(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.updateSyncState(ids, SyncStatus.SYNCED, System.currentTimeMillis())
    }

    suspend fun markFailed(id: Long) = dao.updateSyncState(id, SyncStatus.FAILED, null)

    suspend fun markFailed(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.updateSyncState(ids, SyncStatus.FAILED, null)
    }
}
