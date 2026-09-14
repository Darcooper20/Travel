package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.travelbenefits.app.data.local.entity.AttributionEventEntity
import com.travelbenefits.app.data.local.entity.ExpectationEntity
import com.travelbenefits.app.data.local.entity.TripEventEntity
import com.travelbenefits.app.data.local.entity.TripSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDetailDao {
    @Query("SELECT * FROM trip_segments WHERE tripId = :tripId ORDER BY sequence")
    fun observeSegments(tripId: Long): Flow<List<TripSegmentEntity>>

    @Query("SELECT * FROM trip_segments ORDER BY tripId, sequence")
    fun observeAllSegments(): Flow<List<TripSegmentEntity>>

    @Query("SELECT * FROM trip_segments WHERE tripId = :tripId ORDER BY sequence")
    suspend fun segments(tripId: Long): List<TripSegmentEntity>

    @Insert
    suspend fun insertSegments(segments: List<TripSegmentEntity>)

    @Query("DELETE FROM trip_segments WHERE tripId = :tripId")
    suspend fun deleteSegments(tripId: Long)

    @Query("SELECT * FROM trip_events WHERE tripId = :tripId ORDER BY occurredAt DESC")
    fun observeEvents(tripId: Long): Flow<List<TripEventEntity>>

    @Insert
    suspend fun insertEvent(event: TripEventEntity): Long

    @Query("SELECT * FROM expectations ORDER BY status, dueByEpochDay")
    fun observeExpectations(): Flow<List<ExpectationEntity>>

    @Query("SELECT * FROM expectations")
    suspend fun expectations(): List<ExpectationEntity>

    @Query("SELECT * FROM expectations WHERE refType = :refType AND refId = :refId AND kind = :kind LIMIT 1")
    suspend fun findExpectation(refType: String, refId: String, kind: String): ExpectationEntity?

    @Query("SELECT * FROM expectations WHERE id = :id LIMIT 1")
    suspend fun findExpectationById(id: Long): ExpectationEntity?

    @Insert
    suspend fun insertExpectation(expectation: ExpectationEntity): Long

    @Update
    suspend fun updateExpectation(expectation: ExpectationEntity)

    @Query("SELECT * FROM attribution_events ORDER BY occurredAt DESC")
    fun observeAttribution(): Flow<List<AttributionEventEntity>>

    @Insert
    suspend fun insertAttribution(event: AttributionEventEntity): Long
}
