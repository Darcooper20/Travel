package com.travelbenefits.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.travelbenefits.app.data.local.entity.PointsSnapshotEntity
import com.travelbenefits.app.domain.model.LoyaltyProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsSnapshotDao {
    @Query("SELECT * FROM points_snapshots ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<PointsSnapshotEntity>>

    @Query("SELECT * FROM points_snapshots WHERE program = :program ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun recentForProgram(program: LoyaltyProgram, limit: Int): List<PointsSnapshotEntity>

    @Query("SELECT * FROM points_snapshots WHERE program = :program ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestForProgram(program: LoyaltyProgram): PointsSnapshotEntity?

    @Insert
    suspend fun insert(snapshot: PointsSnapshotEntity): Long

    @Query("DELETE FROM points_snapshots WHERE program = :program")
    suspend fun deleteForProgram(program: LoyaltyProgram)
}
