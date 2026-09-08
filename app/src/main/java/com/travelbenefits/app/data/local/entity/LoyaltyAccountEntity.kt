package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram

@Entity(tableName = "loyalty_accounts")
data class LoyaltyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val program: LoyaltyProgram,
    val membershipNumber: String?,
    val tier: String?,
    val pointsBalance: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val lastUpdated: Long,
)
