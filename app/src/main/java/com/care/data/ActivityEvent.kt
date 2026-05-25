package com.care.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EventType {
    URL,
    APP_USAGE,
    SCREEN_ON,
    SCREEN_OFF,
    UNLOCK,
    PERMISSION_STATUS
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Entity(tableName = "activity_events")
data class ActivityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: EventType,
    val packageName: String? = null,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null,
    val deviceId: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
