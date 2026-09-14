package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.SpendTransaction
import com.travelbenefits.app.domain.model.SpendingCategory
import javax.inject.Inject

/**
 * Guesses a spending category for a merchant name. Order of trust:
 * 1. the user's own transaction history (same merchant, Plaid category) -
 *    still only Plaid's view, so MEDIUM;
 * 2. a keyword table - LOW/MEDIUM;
 * 3. unknown - the UI asks.
 * A Plaid or keyword category is never proof of the issuer's coding.
 */
class MerchantClassifier @Inject constructor() {

    data class Classification(val category: SpendingCategory?, val confidence: Confidence, val source: String)

    fun classify(merchant: String, history: List<SpendTransaction> = emptyList()): Classification {
        val needle = merchant.trim().lowercase()
        if (needle.isBlank()) return Classification(null, Confidence.LOW, "no merchant")
        val fromHistory = history.filter { it.category != null && (it.merchantName ?: it.name).lowercase().contains(needle) }
            .groupingBy { it.category!! }.eachCount().maxByOrNull { it.value }?.key
        if (fromHistory != null) return Classification(fromHistory, Confidence.MEDIUM, "your transaction history (Plaid category)")
        KEYWORDS.firstOrNull { (words, _) -> words.any { needle.contains(it) } }?.let { (_, category) ->
            return Classification(category, Confidence.MEDIUM, "merchant keyword")
        }
        return Classification(null, Confidence.LOW, "unknown merchant")
    }

    private companion object {
        val KEYWORDS: List<Pair<List<String>, SpendingCategory>> = listOf(
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
}
