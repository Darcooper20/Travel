package com.travelbenefits.app.data.catalog

import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.CardCredit
import com.travelbenefits.app.domain.model.HotelBenefit
import com.travelbenefits.app.domain.model.HotelProgram
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.SpendingCategory as C

/**
 * Hand-curated snapshot of ~25 popular US credit cards, used as the fast,
 * offline path for benefit data (the alternative to a live web-search
 * lookup for cards not listed here - see data/remote/AnthropicApi.kt and
 * CardLookupRepository).
 *
 * IMPORTANT ACCURACY NOTE: annual fees, reward rates and credits change
 * often (issuers revise them roughly yearly, sometimes more). This list
 * reflects public terms as understood as of early-to-mid 2025 and WILL
 * drift out of date. Treat every number here as "last known, verify before
 * relying on it" rather than a live fact - the app surfaces [CardCatalogEntry.dataAsOf]
 * and [CardCatalogEntry.notes] in the UI for exactly that reason. This is
 * not a substitute for checking the issuer's own terms page.
 */
object CardCatalog {

    private const val AS_OF = "early 2025 (manually curated - verify at issuer site)"

    val entries: List<CardCatalogEntry> = listOf(
        CardCatalogEntry(
            id = "chase_sapphire_preferred",
            displayName = "Chase Sapphire Preferred",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.CHASE_UR,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.TRAVEL_GENERAL, 5.0, "Travel booked through Chase Travel"),
                RewardRate(C.DINING, 3.0),
                RewardRate(C.STREAMING, 3.0),
                RewardRate(C.GROCERIES, 3.0, "Online groceries; excludes Target/Walmart/Costco"),
                RewardRate(C.AIRFARE, 2.0),
                RewardRate(C.HOTELS, 2.0),
            ),
            credits = listOf(
                CardCredit("Hotel credit", 50, "annual", "Up to \$50 back on hotel stays booked through Chase Travel."),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "chase_sapphire_reserve",
            displayName = "Chase Sapphire Reserve",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 795,
            rewardCurrency = RewardCurrency.CHASE_UR,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.TRAVEL_GENERAL, 8.0, "Travel booked through Chase Travel"),
                RewardRate(C.DINING, 3.0),
            ),
            credits = listOf(
                CardCredit("Travel credit", 300, "annual", "General travel purchase credit."),
                CardCredit("Global Entry/TSA PreCheck/NEXUS credit", 120, "every 4 years", "Application fee reimbursement."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.MARRIOTT_BONVOY, "Complimentary lounge/dining perks vary", "Access via The Edit / Sapphire Reserve travel program, not automatic elite status."),
            ),
            dataAsOf = AS_OF,
            notes = "Annual fee jumped sharply in the 2025 refresh (was \$550) with added credits (dining, StubHub, Apple TV+/Music, Priority Pass) - the credit list above is not exhaustive; check chase.com.",
        ),
        CardCatalogEntry(
            id = "chase_freedom_unlimited",
            displayName = "Chase Freedom Unlimited",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CHASE_UR,
            baseMultiplier = 1.5,
            categoryRates = listOf(
                RewardRate(C.TRAVEL_GENERAL, 5.0, "Travel booked through Chase Travel"),
                RewardRate(C.DINING, 3.0),
                RewardRate(C.DRUGSTORES, 3.0),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "chase_freedom_flex",
            displayName = "Chase Freedom Flex",
            issuer = "Chase",
            network = "Mastercard",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CHASE_UR,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.DINING, 3.0),
                RewardRate(C.DRUGSTORES, 3.0),
                RewardRate(C.TRAVEL_GENERAL, 5.0, "Travel booked through Chase Travel"),
            ),
            dataAsOf = AS_OF,
            notes = "Also earns 5% (multiplier 5.0) in rotating quarterly categories up to \$1,500/quarter after activation - not modeled per-category here since it changes every 3 months; check the current quarter's categories in the Chase app.",
        ),
        CardCatalogEntry(
            id = "amex_gold",
            displayName = "American Express Gold Card",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 325,
            rewardCurrency = RewardCurrency.AMEX_MR,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.DINING, 4.0, "US restaurants, capped at \$50k/year in purchases"),
                RewardRate(C.GROCERIES, 4.0, "US supermarkets, capped at \$25k/year"),
                RewardRate(C.AIRFARE, 3.0, "Booked directly with airlines or via amextravel.com"),
            ),
            credits = listOf(
                CardCredit("Dining credit", 120, "monthly (\$10/mo)", "Grubhub, Cheesecake Factory, Goldbelly and other partners."),
                CardCredit("Uber Cash", 120, "monthly (\$10/mo)", "Uber rides and Uber Eats in the US."),
                CardCredit("Resort credit", 100, "annual", "At select Amex Fine Hotels + Resorts / The Hotel Collection properties."),
            ),
            dataAsOf = AS_OF,
            notes = "Annual fee rose to \$325 (from \$250) in the 2025 refresh, with credits restructured - verify current credit partners, they rotate.",
        ),
        CardCatalogEntry(
            id = "amex_platinum",
            displayName = "The Platinum Card from American Express",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 895,
            rewardCurrency = RewardCurrency.AMEX_MR,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.AIRFARE, 5.0, "Booked directly or via Amex Travel, capped at \$500k/year"),
                RewardRate(C.HOTELS, 5.0, "Prepaid room rates booked via Amex Travel"),
            ),
            credits = listOf(
                CardCredit("Airline fee credit", 200, "annual", "Incidental fees with one selected airline."),
                CardCredit("Uber Cash", 200, "annual (\$15-20/mo)", "Uber rides and Uber Eats in the US."),
                CardCredit("CLEAR Plus credit", 199, "annual", "Expedited airport security membership."),
                CardCredit("Hotel credit", 200, "annual", "Prepaid Fine Hotels + Resorts / The Hotel Collection bookings."),
                CardCredit("Global Entry/TSA PreCheck credit", 120, "every 4-4.5 years", "Application fee reimbursement."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.MARRIOTT_BONVOY, "Gold elite status", "Complimentary Marriott Bonvoy Gold via enrollment."),
                HotelBenefit(HotelProgram.HILTON_HONORS, "Gold status", "Complimentary Hilton Honors Gold via enrollment."),
            ),
            dataAsOf = AS_OF,
            notes = "Annual fee rose to \$895 (from \$695) in the 2025 refresh with several credits added/changed - this list is not exhaustive (there are more niche credits, e.g. Saks, digital entertainment, Walmart+). Also includes Centurion Lounge and Priority Pass Select lounge access, not modeled as a dollar credit here.",
        ),
        CardCatalogEntry(
            id = "amex_blue_cash_preferred",
            displayName = "Blue Cash Preferred Card from American Express",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.GROCERIES, 6.0, "US supermarkets, capped at \$6k/year then 1%"),
                RewardRate(C.STREAMING, 6.0, "Select US streaming subscriptions"),
                RewardRate(C.TRANSIT, 3.0),
                RewardRate(C.GAS_EV, 3.0, "US gas stations"),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "amex_blue_cash_everyday",
            displayName = "Blue Cash Everyday Card from American Express",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.GROCERIES, 3.0, "US supermarkets, capped at \$6k/year then 1%"),
                RewardRate(C.GAS_EV, 3.0, "US gas stations, capped at \$6k/year"),
                RewardRate(C.ONLINE_SHOPPING, 3.0, "US online retail, capped at \$6k/year"),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "capital_one_venture",
            displayName = "Capital One Venture Rewards",
            issuer = "Capital One",
            network = "Visa/Mastercard",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.CAPITAL_ONE_MILES,
            baseMultiplier = 2.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 5.0, "Booked via Capital One Travel"),
                RewardRate(C.AIRFARE, 5.0, "Booked via Capital One Travel"),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "capital_one_venture_x",
            displayName = "Capital One Venture X",
            issuer = "Capital One",
            network = "Visa",
            annualFeeUsd = 395,
            rewardCurrency = RewardCurrency.CAPITAL_ONE_MILES,
            baseMultiplier = 2.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 10.0, "Booked via Capital One Travel"),
                RewardRate(C.AIRFARE, 5.0, "Booked via Capital One Travel"),
            ),
            credits = listOf(
                CardCredit("Travel credit", 300, "annual", "Credit toward bookings made through Capital One Travel."),
                CardCredit("Anniversary bonus miles", 100, "annual", "10,000 bonus miles each account anniversary (~\$100 in travel value)."),
            ),
            dataAsOf = AS_OF,
            notes = "Also includes Capital One Lounge and Priority Pass access, not modeled as a dollar credit here.",
        ),
        CardCatalogEntry(
            id = "capital_one_savorone",
            displayName = "Capital One SavorOne Cash Rewards",
            issuer = "Capital One",
            network = "Mastercard",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.DINING, 3.0),
                RewardRate(C.ENTERTAINMENT, 3.0),
                RewardRate(C.STREAMING, 3.0),
                RewardRate(C.GROCERIES, 3.0, "Grocery stores, excluding superstores like Walmart/Target"),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "citi_strata_premier",
            displayName = "Citi Strata Premier",
            issuer = "Citi",
            network = "Mastercard",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.CITI_TYP,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.AIRFARE, 3.0),
                RewardRate(C.HOTELS, 3.0),
                RewardRate(C.DINING, 3.0),
                RewardRate(C.GROCERIES, 3.0, "Supermarkets"),
                RewardRate(C.GAS_EV, 3.0),
            ),
            credits = listOf(
                CardCredit("Hotel credit", 100, "annual", "On a single hotel stay of \$500+ (before taxes/fees) booked via Citi Travel."),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "citi_double_cash",
            displayName = "Citi Double Cash",
            issuer = "Citi",
            network = "Mastercard",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 2.0,
            categoryRates = emptyList(),
            dataAsOf = AS_OF,
            notes = "2% = 1% when you buy, 1% when you pay it off. Now earns Citi ThankYou Points redeemable as cash, so this can be pooled with a ThankYou Premier-type card - simplified here as flat cash back.",
        ),
        CardCatalogEntry(
            id = "citi_custom_cash",
            displayName = "Citi Custom Cash",
            issuer = "Citi",
            network = "Mastercard",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.DINING, 5.0, "Whichever eligible category you spend most in that billing cycle, capped at \$500/cycle - shown here for dining as one example"),
            ),
            dataAsOf = AS_OF,
            notes = "5% automatically applies to your top spending category each cycle from a fixed list (dining, groceries, gas, travel, drugstores, home improvement, live entertainment, select streaming, fitness clubs) - only one category per cycle, capped at \$500 spend/cycle.",
        ),
        CardCatalogEntry(
            id = "discover_it_cash_back",
            displayName = "Discover it Cash Back",
            issuer = "Discover",
            network = "Discover",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.DISCOVER_CASHBACK,
            baseMultiplier = 1.0,
            categoryRates = emptyList(),
            dataAsOf = AS_OF,
            notes = "5% (multiplier 5.0) in rotating quarterly categories up to \$1,500/quarter after activation, 1% elsewhere - not modeled per-category since it changes every 3 months. New cardholders also get all cash back matched at the end of the first year.",
        ),
        CardCatalogEntry(
            id = "boa_customized_cash_rewards",
            displayName = "Bank of America Customized Cash Rewards",
            issuer = "Bank of America",
            network = "Visa",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.BANK_OF_AMERICA_CASH,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.GROCERIES, 2.0),
                RewardRate(C.GAS_EV, 3.0, "If chosen as your 3% category - see note"),
            ),
            dataAsOf = AS_OF,
            notes = "3% goes to ONE category you choose each month (gas/EV, online shopping, dining, travel, drugstores, or home improvement/furnishings), plus 2% at grocery stores/wholesale clubs, combined cap \$2,500/quarter. Preferred Rewards clients earn 25-75% more. Shown here defaulted to gas/EV as the chosen category.",
        ),
        CardCatalogEntry(
            id = "wells_fargo_active_cash",
            displayName = "Wells Fargo Active Cash",
            issuer = "Wells Fargo",
            network = "Visa",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = 2.0,
            categoryRates = emptyList(),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "wells_fargo_autograph",
            displayName = "Wells Fargo Autograph",
            issuer = "Wells Fargo",
            network = "Visa",
            annualFeeUsd = 0,
            rewardCurrency = RewardCurrency.WELLS_REWARDS,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.DINING, 3.0),
                RewardRate(C.GAS_EV, 3.0),
                RewardRate(C.TRANSIT, 3.0),
                RewardRate(C.STREAMING, 3.0),
                RewardRate(C.TRAVEL_GENERAL, 3.0),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "marriott_bonvoy_boundless",
            displayName = "Marriott Bonvoy Boundless (Chase)",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.MARRIOTT_BONVOY_POINTS,
            baseMultiplier = 2.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 6.0, "Marriott Bonvoy hotels"),
                RewardRate(C.DINING, 3.0),
                RewardRate(C.GAS_EV, 3.0),
            ),
            credits = listOf(
                CardCredit("Free Night Award", 200, "annual (after account anniversary)", "One free night certificate, typically valid up to a set point value (issuer-set, often ~35k points) plus up to 15,000 points you can add."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.MARRIOTT_BONVOY, "Automatic Silver Elite status", "Plus 15 elite night credits toward status each year."),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "marriott_bonvoy_brilliant",
            displayName = "Marriott Bonvoy Brilliant (Amex)",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 650,
            rewardCurrency = RewardCurrency.MARRIOTT_BONVOY_POINTS,
            baseMultiplier = 2.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 6.0, "Marriott Bonvoy hotels"),
                RewardRate(C.DINING, 3.0),
                RewardRate(C.AIRFARE, 3.0, "Booked directly with airlines"),
            ),
            credits = listOf(
                CardCredit("Free Night Award", 500, "annual (after account anniversary)", "One free night certificate, typically valid up to a higher point value than the Boundless card (issuer-set, often ~85k points)."),
                CardCredit("Marriott property credit", 300, "annual", "Statement credit at Marriott properties, split across stays."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.MARRIOTT_BONVOY, "Automatic Platinum Elite status", "Plus 25 elite night credits toward status each year."),
            ),
            dataAsOf = AS_OF,
            notes = "Also includes Priority Pass Select lounge access and a Global Entry/TSA PreCheck credit, not modeled as a dollar credit here.",
        ),
        CardCatalogEntry(
            id = "hilton_honors_surpass",
            displayName = "Hilton Honors American Express Surpass",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 150,
            rewardCurrency = RewardCurrency.HILTON_HONORS_POINTS,
            baseMultiplier = 3.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 12.0, "Hilton portfolio hotels"),
                RewardRate(C.DINING, 6.0, "US restaurants"),
                RewardRate(C.GROCERIES, 6.0, "US supermarkets"),
                RewardRate(C.GAS_EV, 6.0, "US gas stations"),
            ),
            credits = listOf(
                CardCredit("Free Night Reward", 150, "annual", "After \$15,000 in eligible purchases in a calendar year."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.HILTON_HONORS, "Automatic Gold status", "Upgrade to Diamond after \$40,000 in calendar-year spend."),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "hilton_honors_aspire",
            displayName = "Hilton Honors American Express Aspire",
            issuer = "American Express",
            network = "Amex",
            annualFeeUsd = 550,
            rewardCurrency = RewardCurrency.HILTON_HONORS_POINTS,
            baseMultiplier = 3.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 14.0, "Hilton portfolio hotels"),
                RewardRate(C.AIRFARE, 7.0, "Flights booked directly with airlines"),
                RewardRate(C.DINING, 7.0, "US restaurants"),
            ),
            credits = listOf(
                CardCredit("Hilton resort credit", 200, "annual (2x \$100 semi-annual)", "Statement credits at eligible Hilton resorts."),
                CardCredit("Airline fee credit", 200, "annual (2x \$100 semi-annual)", "Incidental fees with one selected airline."),
                CardCredit("Free Night Reward", 300, "annual", "One night, any Hilton property/category."),
                CardCredit("CLEAR Plus credit", 189, "annual", "Expedited airport security membership."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.HILTON_HONORS, "Automatic Diamond status", "Top Hilton Honors tier, no spend threshold."),
            ),
            dataAsOf = AS_OF,
            notes = "Annual fee is understood to have risen to \$550 (from \$450) in a 2025 refresh alongside added credits - this is the figure with the least certainty in this catalog; confirm the current fee and credit structure at americanexpress.com before assuming it's accurate.",
        ),
        CardCatalogEntry(
            id = "world_of_hyatt",
            displayName = "World of Hyatt Credit Card (Chase)",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 95,
            rewardCurrency = RewardCurrency.WORLD_OF_HYATT_POINTS,
            baseMultiplier = 1.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 4.0, "Hyatt hotels"),
                RewardRate(C.DINING, 2.0),
                RewardRate(C.TRANSIT, 2.0, "Local transit and commuting"),
                RewardRate(C.ENTERTAINMENT, 2.0, "Gym memberships"),
                RewardRate(C.AIRFARE, 2.0, "Airline tickets purchased directly"),
            ),
            credits = listOf(
                CardCredit("Free Night Award", 200, "annual (after account anniversary)", "One free night, category 1-4 Hyatt property."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.WORLD_OF_HYATT, "Automatic Discoverist status", "Plus 5 elite night credits toward status each year."),
            ),
            dataAsOf = AS_OF,
        ),
        CardCatalogEntry(
            id = "ihg_one_rewards_premier",
            displayName = "IHG One Rewards Premier (Chase)",
            issuer = "Chase",
            network = "Visa",
            annualFeeUsd = 99,
            rewardCurrency = RewardCurrency.IHG_ONE_REWARDS_POINTS,
            baseMultiplier = 3.0,
            categoryRates = listOf(
                RewardRate(C.HOTELS, 10.0, "IHG hotels"),
                RewardRate(C.TRAVEL_GENERAL, 5.0),
                RewardRate(C.GAS_EV, 5.0),
                RewardRate(C.DINING, 5.0),
            ),
            credits = listOf(
                CardCredit("Free Night Award", 130, "annual (after account anniversary)", "One free night, up to 40,000 points in value, after meeting an annual spend threshold."),
            ),
            hotelBenefits = listOf(
                HotelBenefit(HotelProgram.IHG_ONE_REWARDS, "Automatic Platinum Elite status", "Plus a 4th night free on IHG reward bookings of 4+ nights."),
            ),
            dataAsOf = AS_OF,
        ),
    )

    fun findById(id: String): CardCatalogEntry? = entries.firstOrNull { it.id == id }

    /** Simple case-insensitive substring search over display name/issuer, for the "add a card" screen. */
    fun search(query: String): List<CardCatalogEntry> {
        if (query.isBlank()) return entries
        val q = query.trim().lowercase()
        return entries.filter { it.displayName.lowercase().contains(q) || it.issuer.lowercase().contains(q) }
    }
}
