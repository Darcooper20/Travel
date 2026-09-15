package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.domain.PromptGuard
import com.travelbenefits.app.data.remote.anthropic.AnthropicClient
import com.travelbenefits.app.data.remote.anthropic.AnthropicTool
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Ask about a redemption": the user describes a trip ("3 nights in Lisbon
 * in March, hotel", "SFO to Tokyo business class in October") and Claude,
 * with live web search, works out the best ways to use the points they
 * actually hold. No award-search API is public for most programs, so this
 * is research + reasoning, not a live inventory check - the answer always
 * says so and links to the program's own search page for confirmation.
 *
 * Only balances, tiers and card names are sent - never membership numbers
 * or email content.
 */
@Singleton
class PointsAdvisorRepository @Inject constructor(
    private val anthropicClient: AnthropicClient,
    private val securePrefs: SecurePrefs,
) {
    suspend fun ask(question: String, accounts: List<LoyaltyAccount>, cards: List<ResolvedWalletCard>): Result<String> {
        val apiKey = securePrefs.anthropicApiKey
        if (apiKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Add an Anthropic API key in Settings to use the points advisor."))
        }
        val context = buildContext(accounts, cards)
        return runCatching {
            anthropicClient.sendAndGetFinalText(
                apiKey = apiKey,
                system = PromptGuard.harden(SYSTEM_PROMPT),
                userText = "My points and cards (app records, some extracted from email):\n" + PromptGuard.wrapUntrusted("app records", context) + "\n\nQuestion: " + PromptGuard.neutralise(question),
                maxTokens = 3000,
                tools = listOf(AnthropicTool(type = "web_search_20260209", name = "web_search", maxUses = 6)),
            )
        }
    }

    private fun buildContext(accounts: List<LoyaltyAccount>, cards: List<ResolvedWalletCard>): String = buildString {
        if (accounts.isEmpty()) append("- No loyalty balances recorded yet.\n")
        accounts.forEach { a ->
            val profile = LoyaltyProgramCatalog.profileFor(a.program)
            append("- ${a.program.displayName} (${a.program.kind.label.lowercase()}): ")
            val numeric = a.pointsNumeric
            append(if (numeric != null) profile.formatBalance(numeric) else a.pointsBalance ?: "balance unknown")
            a.tier?.let { append(", status $it") }
            append(" (app's estimate ~${profile.estValueCentsPerPoint}¢/pt)\n")
        }
        if (cards.isEmpty()) append("- No credit cards recorded.\n")
        cards.forEach { c ->
            val currency: RewardCurrency? = (c as? ResolvedWalletCard.Catalog)?.entry?.rewardCurrency
            append("- Card: ${c.displayName}")
            c.walletCard.rewardsBalance?.let { append(", balance ${String.format("%,d", it)} ${currency?.displayName ?: "rewards"}") }
            currency?.let { cur ->
                append(" (earns ${cur.displayName}")
                val partners = TransferPartnerCatalog.partnersFrom(cur)
                if (partners.isNotEmpty()) {
                    append("; transfers to ").append(partners.joinToString { "${it.to.displayName} ${it.ratioLabel()}" })
                }
                append(")")
            }
            append('\n')
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You are a points-and-miles travel advisor helping one person get the
            most from the loyalty balances and credit cards listed in their
            message. Use web search to check current award pricing, transfer
            bonuses, sweet spots and any recent program changes relevant to the
            question. Then answer concisely in plain text (no markdown tables;
            short headed sections and dashes for lists are fine):
            1. Best option(s) using points they already have, with the rough points
               cost, taxes/fees, and cents-per-point value.
            2. Whether transferring credit-card points would beat that, and to which
               program at what ratio.
            3. When paying cash and earning is the smarter move.
            4. Exactly where to search/book (program name and site) to confirm live
               availability - be clear that you cannot see live award inventory and
               that pricing is dynamic.
            Quantify uncertainty, never invent availability, and prefer citing what
            the program itself publishes over blogs. Keep it under ~350 words.
        """.trimIndent()
    }
}
