package com.travelbenefits.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Home", Icons.Filled.Home)
    data object Wallet : Screen("wallet", "Wallet", Icons.Filled.CreditCard)
    data object Recommend : Screen("recommend", "Best Card", Icons.Filled.Stars)
    data object Hotels : Screen("hotels", "Hotels", Icons.Filled.Hotel)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val bottomBarScreens = listOf(Dashboard, Wallet, Recommend, Hotels, Settings)
    }
}
