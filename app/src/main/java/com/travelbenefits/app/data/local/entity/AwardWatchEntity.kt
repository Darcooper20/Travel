package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyProgram

@Entity(tableName = "award_watches")
data class AwardWatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val program: LoyaltyProgram?,
    val origin: String?,
    val destination: String?,
    val dateFrom: String?,
    val dateTo: String?,
    val notes: String?,
    val active: Boolean,
    val createdAt: Long,
    val lastCheckedAt: Long?,
    val lastResult: String?,
    val lastFound: Boolean,
)
