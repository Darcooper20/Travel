package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.HotelProgram
import com.travelbenefits.app.domain.model.LoyaltyAccountSource

@Entity(tableName = "loyalty_accounts")
data class LoyaltyAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val program: HotelProgram,
    val membershipNumber: String?,
    val tier: String?,
    val pointsBalance: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val lastUpdated: Long,
)
