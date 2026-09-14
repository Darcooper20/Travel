package com.travelbenefits.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Loyalty : Screen("loyalty", "Loyalty", Icons.Filled.Loyalty)
    data object Trips : Screen("trips", "Trips", Icons.Filled.Luggage)
    data object Optimize : Screen("optimize", "Maximize", Icons.Filled.AutoAwesome)
    data object Wallet : Screen("wallet", "Cards", Icons.Filled.CreditCard)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object Benefits : Screen("benefits", "Credits & certificates", Icons.Filled.CardGiftcard)
    data object CardValue : Screen("cardvalue", "Card value", Icons.Filled.CreditCard)
    data object Reconcile : Screen("reconcile", "Expected vs received", Icons.Filled.CreditCard)
    data object TripDetail : Screen("trip/{tripId}", "Trip", Icons.Filled.Luggage) {
        fun route(id: Long) = "trip/$id"
    }

    companion object {
        val bottomBarScreens = listOf(Home, Loyalty, Trips, Optimize, Wallet)
    }
}
