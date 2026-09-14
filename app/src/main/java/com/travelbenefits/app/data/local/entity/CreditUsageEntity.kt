package com.travelbenefits.app.data.local.entity

import androidx.room.Entity

/** When the user last used a catalog card credit on a specific wallet card. Availability is derived from this plus the credit's period. */
@Entity(tableName = "credit_usage", primaryKeys = ["walletCardId", "creditLabel"])
data class CreditUsageEntity(
    val walletCardId: Long,
    val creditLabel: String,
    val lastUsedAt: Long,
)
