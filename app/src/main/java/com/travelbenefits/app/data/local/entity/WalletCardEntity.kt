package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet_cards")
data class WalletCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String?,
    /** Non-null when this card matches an entry in the built-in catalog (data/catalog/CardCatalog.kt). */
    val catalogCardId: String?,
    /** Non-null when the user added a card not in the catalog; benefits come from [CardLookupCacheEntity] instead. */
    val customCardName: String?,
    val dateAdded: Long,
    val notes: String?,
    /** Added in DB v3 - all nullable so the ALTER TABLE migration stays trivial. */
    val rewardsBalance: Long? = null,
    val rewardsBalanceAsOf: Long? = null,
    val last4: String? = null,
    /** Added in DB v4. */
    val dateOpenedEpochDay: Long? = null,
    val bonusSpendRequiredUsd: Long? = null,
    val bonusDeadlineEpochDay: Long? = null,
    val bonusSpendToDateUsd: Long? = null,
    val bonusEarnedAt: Long? = null,
    val memberName: String? = null,
    /** Added in DB v9: true when this card is an authorized-user card on someone else's account. */
    val isAuthorizedUser: Boolean? = null,
)
