package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One linked bank/issuer login at Plaid (an "Item"). The access token lives on the backend, never here. */
@Entity(tableName = "plaid_items")
data class PlaidItemEntity(
    @PrimaryKey val itemId: String,
    val institutionName: String?,
    /** transactions/sync cursor; null until the first successful sync. */
    val cursor: String?,
    val createdAt: Long,
    val lastSyncAt: Long?,
    val lastError: String?,
)

/** A card/bank account under an item, optionally mapped to a wallet card so its spend can be judged against the wallet. */
@Entity(tableName = "plaid_accounts")
data class PlaidAccountEntity(
    @PrimaryKey val accountId: String,
    val itemId: String,
    val name: String,
    val officialName: String?,
    val mask: String?,
    val type: String?,
    val subtype: String?,
    val walletCardId: Long?,
)

/** One purchase. Amount is positive for spend (Plaid's sign convention), negative for refunds/payments. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val accountId: String,
    val amount: Double,
    val epochDay: Long,
    val name: String,
    val merchantName: String?,
    val pfcPrimary: String?,
    val pfcDetailed: String?,
    /** SpendingCategory name after mapping, or null when it isn't spend (transfers, payments, income). */
    val spendingCategory: String?,
    val pending: Boolean,
)
