package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.AwardWatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AwardWatchDao {
    @Query("SELECT * FROM award_watches ORDER BY active DESC, createdAt DESC")
    fun observeAll(): Flow<List<AwardWatchEntity>>

    @Query("SELECT * FROM award_watches WHERE active = 1 ORDER BY lastCheckedAt IS NOT NULL, lastCheckedAt ASC")
    suspend fun getActive(): List<AwardWatchEntity>

    @Query("SELECT * FROM award_watches")
    suspend fun getAll(): List<AwardWatchEntity>

    @Query("SELECT * FROM award_watches WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AwardWatchEntity?

    @Insert
    suspend fun insert(watch: AwardWatchEntity): Long

    @Update
    suspend fun update(watch: AwardWatchEntity)

    @Query("DELETE FROM award_watches WHERE id = :id")
    suspend fun deleteById(id: Long)
}
