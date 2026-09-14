package com.travelbenefits.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.LedgerSource

/**
 * One movement against a card credit's allowance. Amounts are integer
 * cents, always positive; [kind] gives the direction. Replaces the old
 * `credit_usage` last-used flag (migrated as USED_UNKNOWN_AMOUNT, never as
 * an invented amount).
 */
@Entity(tableName = "benefit_ledger")
data class BenefitLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walletCardId: Long,
    val creditLabel: String,
    val kind: LedgerEntryKind,
    val amountCents: Long,
    /** Day the charge/credit happened (epoch day), used to place it in a period. */
    val epochDay: Long,
    val transactionId: String?,
    val note: String?,
    val source: LedgerSource,
    /** True for machine-proposed matches the user hasn't confirmed; excluded from confirmed totals. */
    val needsConfirmation: Boolean,
    val createdAt: Long,
)
