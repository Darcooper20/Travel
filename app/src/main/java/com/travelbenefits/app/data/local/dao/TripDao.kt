package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startEpochDay IS NULL, startEpochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE confirmationNumber = :confirmationNumber AND provider = :provider LIMIT 1")
    suspend fun findByConfirmation(confirmationNumber: String, provider: String): TripEntity?

    @Query("SELECT * FROM trips WHERE sourceMessageId = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: String): TripEntity?

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)
}
