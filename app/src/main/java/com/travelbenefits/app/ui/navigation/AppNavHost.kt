package com.travelbenefits.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.travelbenefits.app.ui.benefits.BenefitsScreen
import com.travelbenefits.app.ui.dashboard.DashboardScreen
import com.travelbenefits.app.ui.loyalty.LoyaltyScreen
import com.travelbenefits.app.ui.optimize.OptimizeScreen
import com.travelbenefits.app.ui.settings.SettingsScreen
import com.travelbenefits.app.ui.trips.TripsScreen
import com.travelbenefits.app.ui.wallet.WalletScreen

@Composable
fun AppNavHost(onLaunchGmailAuth: (Intent) -> Unit, onLaunchPlaidLink: (String) -> Unit) {
    val navController = rememberNavController()

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                Screen.bottomBarScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = { navigateTab(screen.route) },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Home.route) {
                DashboardScreen(
                    onOpenLoyalty = { navigateTab(Screen.Loyalty.route) },
                    onOpenTrips = { navigateTab(Screen.Trips.route) },
                    onOpenOptimize = { navigateTab(Screen.Optimize.route) },
                    onOpenWallet = { navigateTab(Screen.Wallet.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenBenefits = { navController.navigate(Screen.Benefits.route) },
                )
            }
            composable(Screen.Benefits.route) {
                BenefitsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Loyalty.route) {
                LoyaltyScreen(onOpenSettings = { navController.navigate(Screen.Settings.route) })
            }
            composable(Screen.Trips.route) { TripsScreen() }
            composable(Screen.Optimize.route) { OptimizeScreen() }
            composable(Screen.Wallet.route) { WalletScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(onLaunchGmailAuth = onLaunchGmailAuth, onLaunchPlaidLink = onLaunchPlaidLink, onBack = { navController.popBackStack() })
            }
        }
    }
}
