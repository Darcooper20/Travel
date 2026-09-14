package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram

@Entity(tableName = "benefit_items")
data class BenefitItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: BenefitKind,
    val title: String,
    val program: LoyaltyProgram?,
    val walletCardId: Long?,
    val valueUsd: Double?,
    val expiresEpochDay: Long?,
    val usedAt: Long?,
    val notes: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val createdAt: Long,
)
