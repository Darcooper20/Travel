package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.ActionEffort
import com.travelbenefits.app.domain.model.ActionItem
import com.travelbenefits.app.domain.model.ActionKind
import com.travelbenefits.app.domain.model.ActionState
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.CreditState
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.PlaidItem
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Turns alerts and connection/ledger state into a prioritised action list.
 * Priority = deadline pressure + money at stake + confidence - effort, all
 * deterministic so the order is stable between refreshes. Persisted states
 * (snooze/dismiss/complete) are applied by key.
 */
class ActionEngine @Inject constructor() {

    data class Inputs(
        val alerts: List<Alert>,
        val cards: List<ResolvedWalletCard>,
        val accounts: List<LoyaltyAccount>,
        val credits: List<CreditStatus>,
        val plaidItems: List<PlaidItem>,
        val gmailConnected: Boolean,
        val autoSyncEnabled: Boolean,
        val lastGmailSyncAt: Long,
        val states: Map<String, Pair<ActionState, Long?>>,
        val today: Long = LocalDate.now().toEpochDay(),
        val now: Long = System.currentTimeMillis(),
    )

    fun build(input: Inputs): List<ActionItem> {
        val items = mutableListOf<ActionItem>()
        input.alerts.forEach { alert -> items += fromAlert(alert, input.today) }

        // Missing reimbursements: eligible charge recorded > 45 days ago with nothing posted this period.
        input.credits.forEach { c ->
            val oldestExpected = c.entries.filter { it.kind == LedgerEntryKind.EXPECTED && !it.needsConfirmation }.minOfOrNull { it.epochDay }
            if (oldestExpected != null && input.today - oldestExpected > 45 && c.receivedCents == 0L) {
                items += ActionItem(
                    key = "reimb:${c.key}:$oldestExpected", kind = ActionKind.MISSING_REIMBURSEMENT,
                    title = "${c.credit.label} on ${c.walletCard.displayName}: credit not seen",
                    reason = "An eligible charge was recorded ${input.today - oldestExpected} days ago and no statement credit has posted.",
                    amountUsd = c.pendingCents / 100.0, deadlineEpochDay = null, confidence = Confidence.MEDIUM, effort = ActionEffort.MEDIUM,
                    source = "Benefit ledger", nextStep = "Check the statement; if missing, message the issuer with the charge date and amount.",
                    destination = Alert.Destination.BENEFITS, walletCardId = c.walletCard.walletCard.id, priority = 0,
                )
            }
            if (c.state == CreditState.NEEDS_CONFIRMATION) {
                items += ActionItem(
                    key = "confirm:${c.key}:${c.window.startEpochDay}", kind = ActionKind.OTHER,
                    title = "Confirm a matched charge for ${c.credit.label}", reason = "A transaction looked like this credit; confirm or reject the match.",
                    amountUsd = null, deadlineEpochDay = null, confidence = Confidence.MEDIUM, effort = ActionEffort.TRIVIAL, source = "Transaction matching",
                    nextStep = "Open Credits & certificates and confirm.", destination = Alert.Destination.BENEFITS, walletCardId = c.walletCard.walletCard.id, priority = 0,
                )
            }
        }

        // Renewals.
        input.cards.forEach { card ->
            val entry = (card as? ResolvedWalletCard.Catalog)?.entry ?: return@forEach
            val opened = card.walletCard.dateOpenedEpochDay ?: return@forEach
            if (entry.annualFeeUsd <= 0) return@forEach
            val openedDate = LocalDate.ofEpochDay(opened)
            val todayDate = LocalDate.ofEpochDay(input.today)
            var anniversary = openedDate.withYear(todayDate.year)
            if (anniversary.isBefore(todayDate)) anniversary = anniversary.plusYears(1)
            val days = anniversary.toEpochDay() - input.today
            if (days in 0..45) {
                items += ActionItem(
                    key = "renewal:${card.walletCard.id}:${anniversary.year}", kind = ActionKind.RENEWAL,
                    title = "${card.displayName}: $${entry.annualFeeUsd} annual fee in $days day(s)",
                    reason = "Decide keep / downgrade / cancel before the fee posts; the card value analysis compares realized value with the fee.",
                    amountUsd = entry.annualFeeUsd.toDouble(), deadlineEpochDay = anniversary.toEpochDay(), confidence = Confidence.HIGH,
                    effort = ActionEffort.MEDIUM, source = "Date opened + catalog fee", nextStep = "Open Card value and review the scenarios.",
                    destination = Alert.Destination.CARDS, walletCardId = card.walletCard.id, priority = 0,
                )
            }
        }

        // Stale balances and broken connections.
        input.accounts.forEach { a ->
            val age = TimeUnit.MILLISECONDS.toDays(input.now - a.lastUpdated)
            if (age > 90) {
                items += ActionItem(
                    key = "stale:${a.id}:${age / 30}", kind = ActionKind.STALE_BALANCE, title = "${a.program.displayName} balance is ${age / 30} month(s) old",
                    reason = "No statement or manual update since ${LocalDate.ofEpochDay(a.lastUpdated / 86_400_000L)}; values built on it are stale.",
                    amountUsd = null, deadlineEpochDay = null, confidence = Confidence.HIGH, effort = ActionEffort.SHORT, source = "Account timestamp",
                    nextStep = "Run a sync or update the balance by hand.", destination = Alert.Destination.LOYALTY, program = a.program, priority = 0,
                )
            }
        }
        input.plaidItems.filter { it.lastError != null }.forEach { item ->
            items += ActionItem(
                key = "plaid:${item.itemId}", kind = ActionKind.BROKEN_CONNECTION, title = "${item.institutionName ?: "Bank"} connection failing",
                reason = item.lastError ?: "", amountUsd = null, deadlineEpochDay = null, confidence = Confidence.HIGH, effort = ActionEffort.SHORT,
                source = "Plaid sync", nextStep = "Re-link the account in Settings.", destination = Alert.Destination.CARDS, priority = 0,
            )
        }
        if (input.autoSyncEnabled && !input.gmailConnected) {
            items += ActionItem(
                key = "gmail:disconnected", kind = ActionKind.BROKEN_CONNECTION, title = "Gmail is disconnected but background sync is on",
                reason = "The monitor can't read new statements until Gmail is reconnected.", amountUsd = null, deadlineEpochDay = null,
                confidence = Confidence.HIGH, effort = ActionEffort.SHORT, source = "Settings", nextStep = "Reconnect Gmail in Settings.",
                destination = Alert.Destination.OPTIMIZE, priority = 0,
            )
        } else if (input.gmailConnected && input.lastGmailSyncAt > 0 && TimeUnit.MILLISECONDS.toDays(input.now - input.lastGmailSyncAt) > 14) {
            items += ActionItem(
                key = "gmail:stale:${input.lastGmailSyncAt / 86_400_000L}", kind = ActionKind.BROKEN_CONNECTION, title = "Email sync hasn't run for 2+ weeks",
                reason = "Balances and trips from email may be out of date.", amountUsd = null, deadlineEpochDay = null, confidence = Confidence.HIGH,
                effort = ActionEffort.TRIVIAL, source = "Sync timestamp", nextStep = "Tap Sync now on Home.", destination = Alert.Destination.OPTIMIZE, priority = 0,
            )
        }

        return items
            .distinctBy { it.key }
            .map { it.copy(priority = score(it, input.today)) }
            .map { item ->
                val (state, until) = input.states[item.key] ?: (ActionState.OPEN to null)
                val effective = if (state == ActionState.SNOOZED && until != null && until <= input.today) ActionState.OPEN else state
                item.copy(state = effective, snoozedUntilEpochDay = until)
            }
            .sortedWith(compareBy<ActionItem> { it.state != ActionState.OPEN }.thenByDescending { it.priority })
    }

    /** 0-100. Deadline pressure dominates, then money, confidence, and effort as a penalty. */
    fun score(item: ActionItem, today: Long): Int {
        val days = item.deadlineEpochDay?.let { it - today }
        val deadlineScore = when {
            days == null -> 15.0 // undated work sits below anything due within 90 days; kind boosts lift the exceptions
            days < 0 -> 50.0
            days <= 3 -> 60.0
            days <= 7 -> 50.0
            days <= 30 -> 35.0
            days <= 90 -> 20.0
            else -> 10.0
        }
        val amount = item.amountUsd ?: 0.0
        val moneyScore = minOf(25.0, amount / 20.0)
        val confidenceScore = when (item.confidence) { Confidence.HIGH -> 10.0; Confidence.MEDIUM -> 6.0; Confidence.LOW -> 2.0 }
        val effortPenalty = when (item.effort) { ActionEffort.TRIVIAL -> 0.0; ActionEffort.SHORT -> 2.0; ActionEffort.MEDIUM -> 5.0; ActionEffort.LONG -> 8.0 }
        val kindBoost = when (item.kind) { ActionKind.BROKEN_CONNECTION -> 5.0; ActionKind.MISSING_REIMBURSEMENT -> 5.0; else -> 0.0 }
        return (deadlineScore + moneyScore + confidenceScore + kindBoost - effortPenalty).roundToInt().coerceIn(0, 100)
    }

    private fun fromAlert(alert: Alert, today: Long): ActionItem {
        val key = alert.notifyKey ?: "alert:${alert.title.hashCode()}"
        val kind = when {
            key.startsWith("expiry:") -> ActionKind.EXPIRING_POINTS
            key.startsWith("credit:") -> ActionKind.EXPIRING_CREDIT
            key.startsWith("benefit:") -> ActionKind.EXPIRING_CERTIFICATE
            key.startsWith("bonus:") -> ActionKind.BONUS_DEADLINE
            key.startsWith("rotate:") -> ActionKind.ACTIVATION
            key.startsWith("trip:") -> ActionKind.TRIP
            key.startsWith("tripnumber:") -> ActionKind.TRIP_LOYALTY
            alert.destination == Alert.Destination.OPTIMIZE -> ActionKind.SPEND
            alert.tripId != null -> ActionKind.TRIP_LOYALTY
            else -> ActionKind.OTHER
        }
        val deadline = Regex("""in (\d+) day""").find(alert.title)?.groupValues?.get(1)?.toLongOrNull()?.let { today + it }
        val amount = Regex("""\$([\d,]+(?:\.\d+)?)""").find(alert.detail + " " + alert.title)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val effort = when (kind) {
            ActionKind.ACTIVATION, ActionKind.TRIP_LOYALTY -> ActionEffort.SHORT
            ActionKind.EXPIRING_POINTS -> ActionEffort.SHORT
            ActionKind.EXPIRING_CREDIT, ActionKind.EXPIRING_CERTIFICATE -> ActionEffort.MEDIUM
            ActionKind.BONUS_DEADLINE -> ActionEffort.LONG
            else -> ActionEffort.TRIVIAL
        }
        val confidence = when (alert.severity) { Alert.Severity.URGENT -> Confidence.HIGH; Alert.Severity.WARNING -> Confidence.MEDIUM; Alert.Severity.INFO -> Confidence.MEDIUM }
        return ActionItem(
            key = key, kind = kind, title = alert.title, reason = alert.detail, amountUsd = amount, deadlineEpochDay = deadline, confidence = confidence,
            effort = effort, source = "Computed from stored data", nextStep = defaultNextStep(kind), destination = alert.destination,
            program = alert.program, tripId = alert.tripId, priority = 0,
        )
    }

    private fun defaultNextStep(kind: ActionKind): String = when (kind) {
        ActionKind.EXPIRING_POINTS -> "Make any qualifying earn or redeem to reset the clock."
        ActionKind.EXPIRING_CREDIT -> "Use the credit before the period ends, then record it."
        ActionKind.EXPIRING_CERTIFICATE -> "Book a stay that fits the certificate, or check whether the program extends."
        ActionKind.ACTIVATION -> "Activate in the issuer app, then mark it activated here."
        ActionKind.BONUS_DEADLINE -> "Route planned spend to this card; don't add spend you wouldn't otherwise make."
        ActionKind.TRIP -> "Check in and review the trip's benefits."
        ActionKind.TRIP_LOYALTY -> "Add the member number to the reservation and mark it confirmed."
        ActionKind.STATUS -> "Nothing to do unless the next stay is already planned."
        else -> "Open the item for details."
    }
}
