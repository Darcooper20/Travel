package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.SpendingCategory

/**
 * Maps Plaid's personal_finance_category taxonomy onto the app's
 * [SpendingCategory]. Returns null for non-spend (income, transfers, card
 * payments, fees) so those never count as "spend on the wrong card".
 */
object PlaidCategoryMapper {

    private val detailed: Map<String, SpendingCategory> = mapOf(
        "FOOD_AND_DRINK_RESTAURANT" to SpendingCategory.DINING,
        "FOOD_AND_DRINK_FAST_FOOD" to SpendingCategory.DINING,
        "FOOD_AND_DRINK_COFFEE" to SpendingCategory.DINING,
        "FOOD_AND_DRINK_BEER_WINE_AND_LIQUOR" to SpendingCategory.DINING,
        "FOOD_AND_DRINK_VENDING_MACHINES" to SpendingCategory.OTHER,
        "FOOD_AND_DRINK_GROCERIES" to SpendingCategory.GROCERIES,
        "FOOD_AND_DRINK_OTHER_FOOD_AND_DRINK" to SpendingCategory.DINING,
        "TRANSPORTATION_GAS" to SpendingCategory.GAS_EV,
        "TRANSPORTATION_PUBLIC_TRANSIT" to SpendingCategory.TRANSIT,
        "TRANSPORTATION_TAXIS_AND_RIDE_SHARES" to SpendingCategory.TRANSIT,
        "TRANSPORTATION_PARKING" to SpendingCategory.TRANSIT,
        "TRANSPORTATION_TOLLS" to SpendingCategory.TRANSIT,
        "TRANSPORTATION_BIKES_AND_SCOOTERS" to SpendingCategory.TRANSIT,
        "TRAVEL_FLIGHTS" to SpendingCategory.AIRFARE,
        "TRAVEL_LODGING" to SpendingCategory.HOTELS,
        "TRAVEL_RENTAL_CARS" to SpendingCategory.TRAVEL_GENERAL,
        "TRAVEL_OTHER_TRAVEL" to SpendingCategory.TRAVEL_GENERAL,
        "GENERAL_MERCHANDISE_ONLINE_MARKETPLACES" to SpendingCategory.ONLINE_SHOPPING,
        "GENERAL_MERCHANDISE_OFFICE_SUPPLIES" to SpendingCategory.OFFICE_SUPPLIES,
        "GENERAL_MERCHANDISE_SUPERSTORES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_ELECTRONICS" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_CLOTHING_AND_ACCESSORIES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_DEPARTMENT_STORES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_CONVENIENCE_STORES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_DISCOUNT_STORES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_GIFTS_AND_NOVELTIES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_PET_SUPPLIES" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_SPORTING_GOODS" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_TOBACCO_AND_VAPE" to SpendingCategory.OTHER,
        "GENERAL_MERCHANDISE_OTHER_GENERAL_MERCHANDISE" to SpendingCategory.OTHER,
        "ENTERTAINMENT_TV_AND_MOVIES" to SpendingCategory.STREAMING,
        "ENTERTAINMENT_MUSIC_AND_AUDIO" to SpendingCategory.STREAMING,
        "ENTERTAINMENT_VIDEO_GAMES" to SpendingCategory.ENTERTAINMENT,
        "ENTERTAINMENT_SPORTING_EVENTS_AMUSEMENT_PARKS_AND_MUSEUMS" to SpendingCategory.ENTERTAINMENT,
        "ENTERTAINMENT_CASINOS_AND_GAMBLING" to SpendingCategory.OTHER,
        "ENTERTAINMENT_OTHER_ENTERTAINMENT" to SpendingCategory.ENTERTAINMENT,
        "MEDICAL_PHARMACIES_AND_SUPPLEMENTS" to SpendingCategory.DRUGSTORES,
        "RENT_AND_UTILITIES_RENT" to SpendingCategory.RENT,
        "RENT_AND_UTILITIES_INTERNET_AND_CABLE" to SpendingCategory.PHONE_INTERNET_CABLE,
        "RENT_AND_UTILITIES_TELEPHONE" to SpendingCategory.PHONE_INTERNET_CABLE,
        "GENERAL_SERVICES_POSTAGE_AND_SHIPPING" to SpendingCategory.SHIPPING,
    )

    private val primary: Map<String, SpendingCategory?> = mapOf(
        "FOOD_AND_DRINK" to SpendingCategory.DINING,
        "TRANSPORTATION" to SpendingCategory.TRANSIT,
        "TRAVEL" to SpendingCategory.TRAVEL_GENERAL,
        "GENERAL_MERCHANDISE" to SpendingCategory.OTHER,
        "ENTERTAINMENT" to SpendingCategory.ENTERTAINMENT,
        "MEDICAL" to SpendingCategory.OTHER,
        "PERSONAL_CARE" to SpendingCategory.OTHER,
        "GENERAL_SERVICES" to SpendingCategory.OTHER,
        "HOME_IMPROVEMENT" to SpendingCategory.OTHER,
        "GOVERNMENT_AND_NON_PROFIT" to SpendingCategory.OTHER,
        "RENT_AND_UTILITIES" to SpendingCategory.OTHER,
        // Not spend:
        "INCOME" to null,
        "TRANSFER_IN" to null,
        "TRANSFER_OUT" to null,
        "LOAN_PAYMENTS" to null,
        "BANK_FEES" to null,
    )

    /**
     * Order of trust:
     *  1. a SPECIFIC Plaid detailed category (it saw the merchant's own coding);
     *  2. Plaid saying explicitly that this is not spend - never second-guessed,
     *     so a card payment or transfer can't be turned into "dining" by its name;
     *  3. the merchant name, which rescues everything Plaid dumps into a vague
     *     bucket ("GENERAL_MERCHANDISE", no category at all) - without this the
     *     misrouted-spend figures silently under-count;
     *  4. Plaid's coarse primary category;
     *  5. OTHER.
     */
    fun map(pfcPrimary: String?, pfcDetailed: String?, merchantOrName: String?): SpendingCategory? {
        val fromDetailed = pfcDetailed?.let { detailed[it] }
        if (fromDetailed != null && fromDetailed != SpendingCategory.OTHER) return fromDetailed

        // Explicit non-spend beats any merchant-name guess.
        val primaryKnown = pfcPrimary != null && pfcPrimary in primary
        if (primaryKnown && primary[pfcPrimary] == null) return null

        MerchantKeywords.categoryFor(merchantOrName)?.let { return it }

        if (fromDetailed != null) return fromDetailed
        if (primaryKnown) return primary[pfcPrimary]
        return SpendingCategory.OTHER
    }
}
