package com.travelbenefits.app.data.catalog

import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.TransferPartner

/**
 * Which transferable credit-card currencies move into which of the loyalty
 * programs this app tracks, and at what ratio.
 *
 * Only partners that are in [LoyaltyProgram] are listed - every bank also
 * has a long tail of foreign airline partners (Air Canada, Avianca, British
 * Airways, Virgin Atlantic, Singapore, Emirates...) that this app doesn't
 * track balances for, so they're intentionally absent. Ratios change (and
 * banks run transfer bonuses) - [TransferPartner.dataAsOf] is the snapshot
 * date and the UI always says "verify before transferring". Transfers are
 * one-way and irreversible, which is why nothing here ever moves points
 * automatically.
 */
object TransferPartnerCatalog {

    private const val AS_OF = "September 2026 snapshot - confirm the ratio on the bank's transfer page before moving points"

    val partners: List<TransferPartner> = listOf(
        // Chase Ultimate Rewards
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.WORLD_OF_HYATT, 1.0, "Generally the highest-value hotel transfer from Chase.", AS_OF),
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.MARRIOTT_BONVOY, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.IHG_ONE_REWARDS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.UNITED_MILEAGEPLUS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.SOUTHWEST_RAPID_REWARDS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CHASE_UR, LoyaltyProgram.JETBLUE_TRUEBLUE, 1.0, dataAsOf = AS_OF),
        // Amex Membership Rewards
        TransferPartner(RewardCurrency.AMEX_MR, LoyaltyProgram.HILTON_HONORS, 2.0, "1:2 - the only major bank currency that doubles into Hilton.", AS_OF),
        TransferPartner(RewardCurrency.AMEX_MR, LoyaltyProgram.MARRIOTT_BONVOY, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.AMEX_MR, LoyaltyProgram.CHOICE_PRIVILEGES, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.AMEX_MR, LoyaltyProgram.DELTA_SKYMILES, 1.0, "Amex charges a small excise-tax offset fee on Delta transfers.", AS_OF),
        TransferPartner(RewardCurrency.AMEX_MR, LoyaltyProgram.JETBLUE_TRUEBLUE, 0.8, "250 MR -> 200 TrueBlue.", AS_OF),
        // Capital One Miles
        TransferPartner(RewardCurrency.CAPITAL_ONE_MILES, LoyaltyProgram.WYNDHAM_REWARDS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CAPITAL_ONE_MILES, LoyaltyProgram.CHOICE_PRIVILEGES, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CAPITAL_ONE_MILES, LoyaltyProgram.ACCOR_LIVE_LIMITLESS, 0.5, "2:1 into Accor's fixed-value points.", AS_OF),
        TransferPartner(RewardCurrency.CAPITAL_ONE_MILES, LoyaltyProgram.JETBLUE_TRUEBLUE, 0.6, "5:3.", AS_OF),
        // Citi ThankYou Points
        TransferPartner(RewardCurrency.CITI_TYP, LoyaltyProgram.WYNDHAM_REWARDS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.CITI_TYP, LoyaltyProgram.CHOICE_PRIVILEGES, 2.0, "1:2 - a strong Choice transfer.", AS_OF),
        TransferPartner(RewardCurrency.CITI_TYP, LoyaltyProgram.JETBLUE_TRUEBLUE, 1.0, "1:1 from premium Citi cards; some no-fee cards transfer at 5:4.", AS_OF),
        // Bilt Rewards
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.WORLD_OF_HYATT, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.MARRIOTT_BONVOY, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.IHG_ONE_REWARDS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.ACCOR_LIVE_LIMITLESS, 0.667, "3:2.", AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.UNITED_MILEAGEPLUS, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.AMERICAN_AADVANTAGE, 1.0, dataAsOf = AS_OF),
        TransferPartner(RewardCurrency.BILT_POINTS, LoyaltyProgram.ATMOS_REWARDS_AIRLINE, 1.0, dataAsOf = AS_OF),
        // Wells Fargo Rewards
        TransferPartner(RewardCurrency.WELLS_REWARDS, LoyaltyProgram.CHOICE_PRIVILEGES, 2.0, "1:2 from Wells Fargo Autograph Journey / Autograph.", AS_OF),
    )

    /** Co-brand currencies are the program's own points - a "transfer" at 1:1 with no bank involved. */
    val nativeCurrency: Map<LoyaltyProgram, RewardCurrency> = mapOf(
        LoyaltyProgram.MARRIOTT_BONVOY to RewardCurrency.MARRIOTT_BONVOY_POINTS,
        LoyaltyProgram.HILTON_HONORS to RewardCurrency.HILTON_HONORS_POINTS,
        LoyaltyProgram.WORLD_OF_HYATT to RewardCurrency.WORLD_OF_HYATT_POINTS,
        LoyaltyProgram.IHG_ONE_REWARDS to RewardCurrency.IHG_ONE_REWARDS_POINTS,
        LoyaltyProgram.DELTA_SKYMILES to RewardCurrency.DELTA_SKYMILES,
        LoyaltyProgram.UNITED_MILEAGEPLUS to RewardCurrency.UNITED_MILEAGEPLUS,
        LoyaltyProgram.SOUTHWEST_RAPID_REWARDS to RewardCurrency.SOUTHWEST_RAPID_REWARDS,
        LoyaltyProgram.AMERICAN_AADVANTAGE to RewardCurrency.AA_ADVANTAGE,
        LoyaltyProgram.ATMOS_REWARDS_AIRLINE to RewardCurrency.ATMOS_REWARDS,
        LoyaltyProgram.JETBLUE_TRUEBLUE to RewardCurrency.JETBLUE_TRUEBLUE,
    )

    fun partnersInto(program: LoyaltyProgram): List<TransferPartner> = partners.filter { it.to == program }

    fun partnersFrom(currency: RewardCurrency): List<TransferPartner> = partners.filter { it.from == currency }

    /** Ratio for moving [currency] into [program]: 1.0 for the program's own co-brand currency, the partner ratio, or null if impossible. */
    fun ratio(currency: RewardCurrency, program: LoyaltyProgram): Double? {
        if (nativeCurrency[program] == currency) return 1.0
        return partners.firstOrNull { it.from == currency && it.to == program }?.ratio
    }
}
