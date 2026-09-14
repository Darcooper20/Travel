package com.travelbenefits.app.domain.model

/** Direction/meaning of a benefit ledger movement. */
enum class LedgerEntryKind(val label: String, val consumesAllowance: Boolean) {
    /** Eligible charge made; reimbursement not yet seen. */
    EXPECTED("Eligible charge, awaiting credit", true),
    /** Issuer shows the credit as pending. */
    PENDING("Credit pending", true),
    /** Credit posted to the statement. */
    POSTED("Credit received", true),
    /** A posted credit was clawed back (refund/return). Reduces received. */
    REVERSED("Credit reversed", false),
    /** Manual correction, positive or negative via note; treated like POSTED. */
    ADJUSTMENT("Adjustment", true),
    /** Migrated from the old checkbox: used at some point, amount not recorded. */
    USED_UNKNOWN_AMOUNT("Used (amount not recorded)", true),
}

enum class LedgerSource { MANUAL, PLAID, GMAIL, MIGRATION }

data class LedgerEntry(
    val id: Long,
    val walletCardId: Long,
    val creditLabel: String,
    val kind: LedgerEntryKind,
    val amountCents: Long,
    val epochDay: Long,
    val transactionId: String?,
    val note: String?,
    val source: LedgerSource,
    val needsConfirmation: Boolean,
    val createdAt: Long,
)

/** Lifecycle state of one credit within one period, derived from its ledger entries. */
enum class CreditState(val label: String) {
    UNUSED("Unused"),
    PARTIALLY_USED("Partially used"),
    PENDING("Pending reimbursement"),
    RECEIVED("Received in full"),
    USED_AMOUNT_UNKNOWN("Used - amount not recorded"),
    EXPIRED("Expired unused"),
    REVERSED("Reversed"),
    NEEDS_CONFIRMATION("Match needs confirmation"),
}

/** A credit period window with its origin explained. */
data class CreditPeriodWindow(
    val startEpochDay: Long,
    val endEpochDay: Long,
    val basis: PeriodBasis,
    /** True when the basis was unknown or needed data we don't have (e.g. no date opened), so calendar was assumed. */
    val isAssumed: Boolean,
)
