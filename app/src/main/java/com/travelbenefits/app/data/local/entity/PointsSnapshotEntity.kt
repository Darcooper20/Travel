package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram

/** One balance reading per program per change, so the Loyalty screen can show a history and the monitor can diff. */
@Entity(tableName = "points_snapshots")
data class PointsSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val program: LoyaltyProgram,
    val points: Long,
    val tier: String?,
    val recordedAt: Long,
    val source: LoyaltyAccountSource,
)
