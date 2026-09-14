package com.travelbenefits.app.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deep-link style requests from widgets/shortcuts/notifications, consumed once by the UI. */
object NavigationRequests {
    const val EXTRA_OPEN = "open"
    const val EXTRA_MERCHANT = "merchant"
    const val OPEN_PURCHASE = "purchase"

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    @Volatile
    private var pendingMerchant: String? = null

    fun requestPurchase(merchant: String?) {
        pendingMerchant = merchant
        _pendingRoute.value = OPEN_PURCHASE
    }

    fun consumeRoute(): String? = _pendingRoute.value.also { _pendingRoute.value = null }

    fun consumePurchaseMerchant(): String? = pendingMerchant.also { pendingMerchant = null }
}
