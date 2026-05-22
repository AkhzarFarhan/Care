package com.care.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {
    @Insert
    suspend fun insert(event: ActivityEvent): Long

    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC LIMIT :limit")
    fun recentEvents(limit: Int = 100): Flow<List<ActivityEvent>>

    @Query("SELECT * FROM activity_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun eventsBetween(from: Long, to: Long): List<ActivityEvent>

    @Query("SELECT * FROM activity_events WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY timestamp ASC LIMIT :limit")
    suspend fun pendingSyncEvents(limit: Int = 100): List<ActivityEvent>

    @Query("UPDATE activity_events SET syncStatus = :status, syncedAt = :syncedAt WHERE id = :id")
    suspend fun updateSyncState(id: Long, status: SyncStatus, syncedAt: Long?)

    @Query("DELETE FROM activity_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM activity_events WHERE syncStatus IN ('PENDING', 'FAILED')")
    fun pendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM activity_events WHERE timestamp >= :from ORDER BY timestamp DESC")
    fun eventsSince(from: Long): Flow<List<ActivityEvent>>
}
