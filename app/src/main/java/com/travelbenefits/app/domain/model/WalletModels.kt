package com.travelbenefits.app.domain.model

import kotlinx.serialization.Serializable

/** A card the user actually holds, combining catalog/lookup benefit data with user-entered metadata. */
data class WalletCard(
    val id: Long,
    val nickname: String?,
    val catalogCardId: String?,
    /** Set when [catalogCardId] is null - benefits came from a live lookup instead of the built-in catalog. */
    val customCardName: String?,
    val dateAdded: Long,
    val notes: String?,
)

enum class LoyaltyAccountSource { MANUAL, GMAIL_SCAN }

data class LoyaltyAccount(
    val id: Long,
    val program: HotelProgram,
    val membershipNumber: String?,
    val tier: String?,
    val pointsBalance: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val lastUpdated: Long,
)

/**
 * Structured result of a live web-search benefit lookup for a card not in the
 * built-in catalog, as returned (and cached) from the Anthropic API. Kept
 * deliberately smaller than [CardCatalogEntry] since a single search pass
 * can't reliably reconstruct every category rate - it's a best-effort
 * summary, and the UI should say so.
 */
@Serializable
data class CardLookupResult(
    val displayName: String,
    val issuer: String,
    val annualFeeUsd: Int?,
    val summary: String,
    val categoryHighlights: List<CategoryHighlight>,
    val credits: List<LookupCredit>,
    val hotelBenefitSummaries: List<String>,
    val sourcesNote: String,
)

@Serializable
data class CategoryHighlight(val categoryLabel: String, val multiplierDescription: String)

@Serializable
data class LookupCredit(val label: String, val description: String)

/** A wallet card together with whatever benefit data we actually have for it. */
sealed class ResolvedWalletCard {
    abstract val walletCard: WalletCard
    abstract val displayName: String

    data class Catalog(
        override val walletCard: WalletCard,
        val entry: CardCatalogEntry,
    ) : ResolvedWalletCard() {
        override val displayName: String get() = walletCard.nickname ?: entry.displayName
    }

    data class Custom(
        override val walletCard: WalletCard,
        val lookup: CardLookupResult?,
    ) : ResolvedWalletCard() {
        override val displayName: String
            get() = walletCard.nickname ?: lookup?.displayName ?: walletCard.customCardName ?: "Unknown card"
    }
}
