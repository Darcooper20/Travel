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
    /** Current rewards balance in the card's own currency (points, miles, or whole dollars of cash back), from a statement email or typed in. */
    val rewardsBalance: Long? = null,
    val rewardsBalanceAsOf: Long? = null,
    /** Last four digits, when known - used to match statement emails to this card. */
    val last4: String? = null,
    val dateOpenedEpochDay: Long? = null,
    /** Welcome-bonus minimum spend, deadline and progress (whole USD). */
    val bonusSpendRequiredUsd: Long? = null,
    val bonusDeadlineEpochDay: Long? = null,
    val bonusSpendToDateUsd: Long? = null,
    val bonusEarnedAt: Long? = null,
    /** Household member who holds this card; null = the app's owner. */
    val memberName: String? = null,
)

enum class LoyaltyAccountSource { MANUAL, GMAIL_SCAN }

data class LoyaltyAccount(
    val id: Long,
    val program: LoyaltyProgram,
    val membershipNumber: String?,
    val tier: String?,
    /** Balance as written in the source ("42,500 points") - kept for display when [pointsNumeric] couldn't be parsed. */
    val pointsBalance: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val lastUpdated: Long,
    /** Parsed numeric balance, when known. */
    val pointsNumeric: Long? = null,
    /** Epoch millis when the points are due to expire (from an email warning or the program's inactivity rule). */
    val pointsExpireAt: Long? = null,
    /** Year-to-date qualifying activity toward the next tier, in the program's own metric (nights, MQDs, Loyalty Points...). */
    val qualifyingProgress: Int? = null,
    /** Epoch millis of the last qualifying activity seen - the anchor for inactivity-based expiration. */
    val lastActivityAt: Long? = null,
    /** Household member who holds this account; null = the app's owner. */
    val memberName: String? = null,
) {
    /** Best available balance, preferring the parsed number. */
    val balanceLabel: String?
        get() = pointsNumeric?.let { formatPoints(it) } ?: pointsBalance

    companion object {
        fun formatPoints(points: Long): String = String.format("%,d", points)

        /** "42,500 points" / "18.2k miles" / "1,200" -> 42500 / 18200 / 1200. Null when nothing numeric is there. */
        fun parsePoints(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val match = Regex("""(\d{1,3}(?:[,\s]\d{3})+|\d+)(?:\.(\d+))?\s*([kKmM])?""").find(raw) ?: return null
            val whole = match.groupValues[1].replace(",", "").replace(" ", "").toLongOrNull() ?: return null
            val fraction = match.groupValues[2]
            val suffix = match.groupValues[3].lowercase()
            val multiplier = when (suffix) {
                "k" -> 1_000.0
                "m" -> 1_000_000.0
                else -> 1.0
            }
            val value = (whole.toString() + (if (fraction.isNotEmpty() && multiplier > 1) ".$fraction" else "")).toDouble() * multiplier
            return value.toLong()
        }
    }
}

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

    /** The currency this card earns, when known from the catalog. */
    val rewardCurrency: RewardCurrency?
        get() = (this as? Catalog)?.entry?.rewardCurrency

    /** Estimated dollar value of the card's current rewards balance, if both balance and currency are known. */
    val rewardsValueUsd: Double?
        get() {
            val balance = walletCard.rewardsBalance ?: return null
            val currency = rewardCurrency ?: return null
            return balance * currency.estValueCentsPerPoint / 100.0
        }

    data class Catalog(
        override val walletCard: WalletCard,
        val entry: CardCatalogEntry,
        /** This quarter's rotating categories as recorded by the user, when the card has them. */
        val rotating: RotatingSelection? = null,
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
