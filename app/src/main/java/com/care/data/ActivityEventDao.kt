package com.care.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {
    @Insert
    suspend fun insert(event: ActivityEvent): Long

    @Query("SELECT * FROM activity_events WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY timestamp ASC LIMIT :limit")
    suspend fun pendingSyncEvents(limit: Int = 100): List<ActivityEvent>

    @Query(
        "SELECT * FROM activity_events " +
            "WHERE syncStatus IN ('PENDING', 'FAILED') AND createdAt <= :beforeCreatedAt " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun pendingSyncEventsBefore(beforeCreatedAt: Long, limit: Int = 1000): List<ActivityEvent>

    @Query("UPDATE activity_events SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :id")
    suspend fun updateSyncState(id: Long, status: SyncStatus, syncedAt: Long?)

    @Query("UPDATE activity_events SET syncStatus = :status, syncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<Long>, status: SyncStatus, syncedAt: Long?)

    @Query("SELECT COUNT(*) FROM activity_events WHERE syncStatus IN ('PENDING', 'FAILED')")
    fun pendingSyncCount(): Flow<Int>
}
