package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.PlaidItem
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import java.util.concurrent.TimeUnit

/** One row of the "where does my data come from and how fresh is it" card. */
data class SourceStatus(
    val name: String,
    val state: State,
    /** Plain-language line under the name: last sync, what it covers, or what is missing. */
    val detail: String,
    val lastSyncAt: Long? = null,
    val error: String? = null,
) {
    enum class State { CONNECTED, ATTENTION, OFF }
}

data class ConnectionStatus(
    val sources: List<SourceStatus>,
    /** Loyalty accounts whose balance has not been refreshed for [ConnectionStatusCalculator.STALE_AFTER_DAYS]. */
    val staleAccounts: List<LoyaltyAccount>,
    val programCount: Int,
    val cardCount: Int,
    val upcomingTripCount: Int,
) {
    val needsAttention: Boolean get() = sources.any { it.state == SourceStatus.State.ATTENTION } || staleAccounts.isNotEmpty()
}

/**
 * Deterministic status for the dashboard. Nothing here guesses: a source is
 * CONNECTED only when its credentials exist and its last run did not error,
 * ATTENTION when it is set up but something is wrong or overdue, OFF when the
 * user has not set it up (which is a perfectly fine way to use the app).
 */
class ConnectionStatusCalculator @javax.inject.Inject constructor() {

    data class Inputs(
        val gmailConnected: Boolean,
        val hasAnthropicKey: Boolean,
        val autoSyncEnabled: Boolean,
        val syncIntervalHours: Int,
        val lastEmailSyncAt: Long,
        val lastEmailSyncSummary: String?,
        /** The last run could not search, download or read part of the mailbox. */
        val lastEmailSyncHadFailures: Boolean,
        val plaidItems: List<PlaidItem>,
        val hasSeatsAeroKey: Boolean,
        val accounts: List<LoyaltyAccount>,
        val cards: List<ResolvedWalletCard>,
        val upcomingTripCount: Int,
        val now: Long,
    )

    fun compute(i: Inputs): ConnectionStatus {
        val sources = mutableListOf<SourceStatus>()

        // Email monitor: needs both Gmail and a model key to do anything.
        sources += when {
            !i.gmailConnected && !i.hasAnthropicKey -> SourceStatus("Email monitor", SourceStatus.State.OFF, "Not set up. Balances, trips and card statements can still be entered by hand.")
            !i.gmailConnected -> SourceStatus("Email monitor", SourceStatus.State.ATTENTION, "Anthropic key saved, Gmail not connected - nothing is being read.")
            !i.hasAnthropicKey -> SourceStatus("Email monitor", SourceStatus.State.ATTENTION, "Gmail connected but no Anthropic key - emails cannot be interpreted.")
            i.lastEmailSyncAt <= 0L -> SourceStatus("Email monitor", SourceStatus.State.ATTENTION, "Connected, never synced. Tap Sync now.")
            else -> {
                val ageHours = TimeUnit.MILLISECONDS.toHours(i.now - i.lastEmailSyncAt)
                val overdue = i.autoSyncEnabled && ageHours > i.syncIntervalHours * 2L + 1
                // A run that partly failed is not "connected and fine": coverage has a hole in it.
                val state = if (overdue || i.lastEmailSyncHadFailures) SourceStatus.State.ATTENTION else SourceStatus.State.CONNECTED
                val detail = buildString {
                    append(
                        when {
                            i.lastEmailSyncHadFailures -> "Last run finished with errors (${describeAge(ageHours)}), so some mail was not read."
                            overdue -> "Background sync is overdue (last run ${describeAge(ageHours)})."
                            else -> "Last sync ${describeAge(ageHours)}."
                        },
                    )
                    append(if (i.autoSyncEnabled) " Auto every ${i.syncIntervalHours} h." else " Background sync off.")
                    i.lastEmailSyncSummary?.takeIf { it.isNotBlank() }?.let { append(' ').append(it) }
                }
                SourceStatus("Email monitor", state, detail, i.lastEmailSyncAt)
            }
        }

        // Plaid: one row per linked institution so a broken one is visible.
        if (i.plaidItems.isEmpty()) {
            sources += SourceStatus("Bank transactions (Plaid)", SourceStatus.State.OFF, "Not linked. Spend-by-category and cap tracking use manual entries only.")
        } else {
            i.plaidItems.forEach { item ->
                val name = "Plaid: ${item.institutionName ?: item.itemId.take(10)}"
                val unmapped = item.accounts.count { it.walletCardId == null }
                sources += when {
                    item.lastError != null -> SourceStatus(name, SourceStatus.State.ATTENTION, "Last sync failed - relink or check the backend.", item.lastSyncAt, item.lastError)
                    item.lastSyncAt == null -> SourceStatus(name, SourceStatus.State.ATTENTION, "Linked but never synced.", null)
                    TimeUnit.MILLISECONDS.toDays(i.now - item.lastSyncAt) >= 3 -> SourceStatus(name, SourceStatus.State.ATTENTION, "Last sync ${describeAge(TimeUnit.MILLISECONDS.toHours(i.now - item.lastSyncAt))} - transactions may be missing.", item.lastSyncAt)
                    else -> SourceStatus(
                        name, SourceStatus.State.CONNECTED,
                        "Last sync ${describeAge(TimeUnit.MILLISECONDS.toHours(i.now - item.lastSyncAt))}. ${item.accounts.size} account(s)" + (if (unmapped > 0) ", $unmapped not mapped to a card" else "") + ".",
                        item.lastSyncAt,
                    )
                }
            }
        }

        sources += if (i.hasSeatsAeroKey) SourceStatus("Award inventory (seats.aero)", SourceStatus.State.CONNECTED, "Key saved. Results are cached inventory with an 'as of' time, not live.")
        else SourceStatus("Award inventory (seats.aero)", SourceStatus.State.OFF, "No key. Award search uses labelled AI research and manual imports only.")

        val staleCutoff = i.now - TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS)
        val stale = i.accounts.filter { it.lastUpdated in 1 until staleCutoff }.sortedBy { it.lastUpdated }

        return ConnectionStatus(
            sources = sources,
            staleAccounts = stale,
            programCount = i.accounts.size,
            cardCount = i.cards.size,
            upcomingTripCount = i.upcomingTripCount,
        )
    }

    private fun describeAge(hours: Long): String = when {
        hours < 1 -> "under an hour ago"
        hours < 48 -> "$hours h ago"
        else -> "${hours / 24} days ago"
    }

    companion object {
        const val STALE_AFTER_DAYS = 60L
    }
}
