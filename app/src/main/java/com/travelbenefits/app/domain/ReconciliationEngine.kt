package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.ClaimDraft
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.Expectation
import com.travelbenefits.app.domain.model.ExpectationKind
import com.travelbenefits.app.domain.model.ExpectationStatus
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.PointsSnapshot
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import java.time.LocalDate
import javax.inject.Inject

/**
 * Compares what was expected with what the records show. Posting delays are
 * normal, so nothing is a discrepancy before its due date; after that,
 * evidence is required to mark it received. Matching is confidence-based
 * and anything ambiguous is left for the user.
 */
class ReconciliationEngine @Inject constructor() {

    sealed class Outcome {
        data class Matched(val received: Double, val evidence: String) : Outcome()
        data class Partial(val received: Double, val evidence: String) : Outcome()
        data class Discrepancy(val received: Double, val evidence: String) : Outcome()
        data object Waiting : Outcome()
        data object NoEvidence : Outcome()
    }

    fun evaluate(
        e: Expectation,
        snapshots: List<PointsSnapshot>,
        cards: List<ResolvedWalletCard>,
        credits: List<CreditStatus>,
        today: LocalDate = LocalDate.now(),
    ): Outcome {
        if (e.status != ExpectationStatus.OPEN) return Outcome.Waiting
        val due = e.dueByEpochDay
        return when (e.kind) {
            ExpectationKind.PROGRAM_POINTS, ExpectationKind.QUALIFYING_ACTIVITY -> {
                val program = e.program ?: return Outcome.NoEvidence
                val since = e.createdAt / 86_400_000L
                val relevant = snapshots.filter { it.program == program }.sortedBy { it.recordedAt }
                val before = relevant.lastOrNull { it.recordedAt / 86_400_000L <= since }
                val after = relevant.lastOrNull()
                if (before == null || after == null || after.recordedAt == before.recordedAt) {
                    return if (today.toEpochDay() > due) Outcome.NoEvidence else Outcome.Waiting
                }
                val delta = (after.points - before.points).toDouble()
                val evidence = "Balance moved ${before.points} → ${after.points} between snapshots"
                when {
                    delta >= e.expectedAmount * 0.9 -> Outcome.Matched(delta, evidence)
                    delta > 0 -> if (today.toEpochDay() > due) Outcome.Partial(delta, evidence) else Outcome.Waiting
                    else -> if (today.toEpochDay() > due) Outcome.Discrepancy(0.0, evidence) else Outcome.Waiting
                }
            }
            ExpectationKind.CARD_REWARDS -> {
                val card = cards.firstOrNull { it.walletCard.id == e.walletCardId } ?: return Outcome.NoEvidence
                val asOf = card.walletCard.rewardsBalanceAsOf ?: return if (today.toEpochDay() > due) Outcome.NoEvidence else Outcome.Waiting
                if (asOf < e.createdAt) return if (today.toEpochDay() > due) Outcome.NoEvidence else Outcome.Waiting
                // A single balance can't prove one purchase's rewards; treat a newer balance as weak evidence and leave the decision to the user.
                Outcome.NoEvidence
            }
            ExpectationKind.CREDIT -> {
                val status = credits.firstOrNull { it.key == e.refId } ?: return Outcome.NoEvidence
                val posted = status.entries.filter { it.kind == LedgerEntryKind.POSTED && !it.needsConfirmation && it.epochDay >= e.createdAt / 86_400_000L }.sumOf { it.amountCents } / 100.0
                when {
                    posted >= e.expectedAmount - 0.01 -> Outcome.Matched(posted, "Posted in the benefit ledger")
                    posted > 0 -> if (today.toEpochDay() > due) Outcome.Partial(posted, "Partial credit posted") else Outcome.Waiting
                    else -> if (today.toEpochDay() > due) Outcome.Discrepancy(0.0, "No credit posted by the due date") else Outcome.Waiting
                }
            }
            ExpectationKind.OTHER -> Outcome.NoEvidence
        }
    }

    /** Deterministic, editable claim text. The app never sends it. */
    fun claimDraft(e: Expectation, cardName: String?, tripTitle: String?): ClaimDraft {
        val what = when (e.kind) {
            ExpectationKind.PROGRAM_POINTS -> "missing ${e.program?.displayName ?: "program"} points"
            ExpectationKind.QUALIFYING_ACTIVITY -> "missing ${e.program?.displayName ?: "program"} qualifying credit"
            ExpectationKind.CARD_REWARDS -> "missing rewards on ${cardName ?: "my card"}"
            ExpectationKind.CREDIT -> "statement credit not received on ${cardName ?: "my card"}"
            ExpectationKind.OTHER -> "missing benefit"
        }
        val body = buildString {
            append("Hello,\n\n")
            append("I'm writing about $what")
            tripTitle?.let { append(" for $it") }
            append(".\n\n")
            append("Expected: ${"%,.0f".format(e.expectedAmount)} ${e.unit}")
            e.receivedAmount?.let { append("\nReceived so far: ${"%,.0f".format(it)} ${e.unit}") }
            append("\nDue by: ${LocalDate.ofEpochDay(e.dueByEpochDay)}")
            e.note?.let { append("\nDetails: $it") }
            append("\n\nPlease post the missing amount or let me know what further information you need.\n\nThank you,\n")
        }
        val checklist = listOfNotNull(
            "Confirmation/reservation number",
            if (e.kind == ExpectationKind.CREDIT || e.kind == ExpectationKind.CARD_REWARDS) "Statement showing the charge (date, merchant, amount)" else null,
            if (e.kind == ExpectationKind.PROGRAM_POINTS || e.kind == ExpectationKind.QUALIFYING_ACTIVITY) "Folio/receipt showing the member number on the stay or flight" else null,
            "Screenshot of the offer terms or benefit guide, if applicable",
        )
        return ClaimDraft("Request: $what", body, checklist)
    }
}
