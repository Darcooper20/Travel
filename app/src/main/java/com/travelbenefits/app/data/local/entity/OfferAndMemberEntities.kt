package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_offers")
data class MerchantOfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walletCardId: Long?,
    val merchant: String,
    val description: String,
    val valueUsd: Double?,
    val percentBack: Double?,
    val minSpendUsd: Double?,
    val expiresEpochDay: Long?,
    val enrolled: Boolean,
    val kind: String,
    val source: String,
    val notes: String?,
    val createdAt: Long,
)

/** Household members. Ownership of cards/accounts is by name (memberName); null = owner. */
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isOwner: Boolean,
    val notes: String?,
)
