package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RewardCurrency

@Entity(tableName = "transfer_bonuses")
data class TransferBonusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCurrency: RewardCurrency,
    val toProgram: LoyaltyProgram,
    val bonusPercent: Int,
    val endsEpochDay: Long?,
    val note: String?,
    val checkedAt: Long,
)
