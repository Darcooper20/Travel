package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.NotifiedAlertEntity

@Dao
interface NotifiedAlertDao {
    @Query("SELECT alertKey FROM notified_alerts WHERE alertKey IN (:keys)")
    suspend fun findExisting(keys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<NotifiedAlertEntity>)

    @Query("DELETE FROM notified_alerts WHERE notifiedAt < :olderThan")
    suspend fun prune(olderThan: Long)

    @Query("DELETE FROM notified_alerts")
    suspend fun clear()
}
