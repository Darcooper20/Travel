package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {
    @Query("SELECT * FROM activity_events ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEventEntity>>

    @Query("SELECT COUNT(*) FROM activity_events WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(event: ActivityEventEntity): Long

    @Query("UPDATE activity_events SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM activity_events WHERE occurredAt < :olderThan")
    suspend fun prune(olderThan: Long)

    @Query("DELETE FROM activity_events")
    suspend fun clear()
}
