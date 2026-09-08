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
)
