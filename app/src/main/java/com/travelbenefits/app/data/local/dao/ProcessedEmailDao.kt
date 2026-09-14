package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.ProcessedEmailEntity

@Dao
interface ProcessedEmailDao {
    @Query("SELECT messageId FROM processed_emails WHERE messageId IN (:ids)")
    suspend fun findExisting(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ProcessedEmailEntity>)

    @Query("DELETE FROM processed_emails")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM processed_emails")
    suspend fun count(): Int
}
