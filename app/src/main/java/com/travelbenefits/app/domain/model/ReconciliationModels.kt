package com.travelbenefits.app.domain.model

enum class ExpectationKind(val label: String) {
    CARD_REWARDS("Card rewards"), PROGRAM_POINTS("Program points"), QUALIFYING_ACTIVITY("Qualifying nights/segments"), CREDIT("Statement credit"), OTHER("Other"),
}

enum class ExpectationStatus(val label: String) { OPEN("Awaiting"), MATCHED("Received"), DISCREPANCY("Discrepancy"), DISMISSED("Dismissed") }

data class Expectation(
    val id: Long,
    val kind: ExpectationKind,
    val refType: String,
    val refId: String,
    val walletCardId: Long?,
    val program: LoyaltyProgram?,
    val expectedAmount: Double,
    val unit: String,
    val dueByEpochDay: Long,
    val status: ExpectationStatus,
    val receivedAmount: Double?,
    val evidence: String?,
    val note: String?,
    val createdAt: Long,
    val resolvedAt: Long?,
)

data class AttributionEvent(val id: Long, val kind: String, val fromCurrency: String?, val toProgram: String?, val amount: Double, val valueUsd: Double?, val occurredAt: Long, val note: String?)

/** A discrepancy with a prepared, editable claim draft. Never submitted by the app. */
data class ClaimDraft(val subject: String, val body: String, val checklist: List<String>)
