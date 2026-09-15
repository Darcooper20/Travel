package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.SpendingCategory

/**
 * Merchant-name keyword table, shared by [MerchantClassifier] (the manual
 * "which card for this purchase?" form) and [PlaidCategoryMapper] (imported
 * transactions). It lives in one place so the two paths cannot disagree
 * about what "Starbucks" is.
 *
 * A keyword match is a guess about the merchant, never proof of how the
 * issuer codes the transaction, so callers label it as such and never
 * present it as certain.
 */
object MerchantKeywords {

    /** Best-guess category for a merchant name, or null when no keyword matches. */
    fun categoryFor(merchant: String?): SpendingCategory? {
        val trimmed = merchant?.trim()?.lowercase().orEmpty()
        if (trimmed.isBlank()) return null
        // Padded so keywords written with a trailing space ("bar ", "max ") match at the end too.
        val haystack = " $trimmed "
        return TABLE.firstOrNull { (words, _) -> words.any { haystack.contains(it) } }?.second
    }

    /** Ordered: the first matching row wins, so put the more specific words earlier. */
    val TABLE: List<Pair<List<String>, SpendingCategory>> = listOf(
        listOf("starbucks", "restaurant", "cafe", "coffee", "chipotle", "mcdonald", "pizza", "grill", "bistro", "diner", "doordash", "uber eats", "grubhub", "sushi", "taco", "burger", "bar ") to SpendingCategory.DINING,
        listOf("whole foods", "trader joe", "kroger", "safeway", "publix", "wegmans", "aldi", "h-e-b", "heb ", "supermarket", "grocery", "market basket", "sprouts") to SpendingCategory.GROCERIES,
        listOf("shell", "chevron", "exxon", "mobil", "bp ", "sunoco", "wawa", "gas station", "fuel", "supercharger", "electrify america", "chargepoint", "evgo") to SpendingCategory.GAS_EV,
        listOf("marriott", "hilton", "hyatt", "ihg", "holiday inn", "sheraton", "westin", "hotel", "resort", "inn ", "airbnb", "vrbo") to SpendingCategory.HOTELS,
        listOf("delta", "united", "american airlines", "southwest", "jetblue", "alaska air", "airline", "airways", "spirit", "frontier") to SpendingCategory.AIRFARE,
        listOf("amazon", "ebay", "etsy", "shopify", "online") to SpendingCategory.ONLINE_SHOPPING,
        listOf("netflix", "spotify", "hulu", "disney+", "hbo", "max ", "apple tv", "youtube premium", "paramount", "peacock") to SpendingCategory.STREAMING,
        listOf("cvs", "walgreens", "rite aid", "pharmacy", "drugstore") to SpendingCategory.DRUGSTORES,
        listOf("uber", "lyft", "metro", "transit", "mta", "bart", "parking", "toll", "amtrak", "train") to SpendingCategory.TRANSIT,
        listOf("ticketmaster", "stubhub", "cinema", "theater", "theatre", "concert", "museum", "amc ") to SpendingCategory.ENTERTAINMENT,
        listOf("rent", "landlord", "bilt") to SpendingCategory.RENT,
        listOf("staples", "office depot", "officemax") to SpendingCategory.OFFICE_SUPPLIES,
        listOf("verizon", "at&t", "t-mobile", "comcast", "xfinity", "spectrum", "internet", "wireless") to SpendingCategory.PHONE_INTERNET_CABLE,
        listOf("fedex", "ups ", "usps", "shipping") to SpendingCategory.SHIPPING,
        listOf("expedia", "booking.com", "hotels.com", "travel", "rental car", "hertz", "avis", "enterprise") to SpendingCategory.TRAVEL_GENERAL,
    )
}
