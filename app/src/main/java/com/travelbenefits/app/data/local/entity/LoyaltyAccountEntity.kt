package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram

/**
 * Schema note: the four trailing nullable columns were added in DB version 2
 * (see Migrations.kt). Keep new columns nullable so future ALTER TABLE
 * migrations stay trivial.
 */
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
    val pointsNumeric: Long? = null,
    val pointsExpireAt: Long? = null,
    val qualifyingProgress: Int? = null,
    val lastActivityAt: Long? = null,
    /** Added in DB v4. Null = the app's owner. */
    val memberName: String? = null,
)
