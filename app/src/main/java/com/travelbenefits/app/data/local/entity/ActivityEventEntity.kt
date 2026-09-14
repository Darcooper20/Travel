package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.domain.model.LoyaltyProgram

@Entity(tableName = "activity_events")
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ActivityKind,
    val program: LoyaltyProgram?,
    val title: String,
    val detail: String?,
    val occurredAt: Long,
    val isRead: Boolean,
)
