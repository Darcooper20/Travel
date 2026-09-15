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
        MerchantKeywords.categoryFor(needle)?.let { return Classification(it, Confidence.MEDIUM, "merchant keyword") }
        return Classification(null, Confidence.LOW, "unknown merchant")
    }
}
