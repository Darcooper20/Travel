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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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
import com.travelbenefits.app.ui.onboarding.OnboardingChoice
import com.travelbenefits.app.ui.onboarding.OnboardingScreen
import com.travelbenefits.app.ui.onboarding.OnboardingViewModel
import com.travelbenefits.app.ui.optimize.OptimizeScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.travelbenefits.app.ui.settings.SettingsScreen
import com.travelbenefits.app.ui.trips.TripsScreen
import com.travelbenefits.app.ui.wallet.WalletScreen

@Composable
fun AppNavHost(onLaunchGmailAuth: (Intent) -> Unit, onLaunchPlaidLink: (String) -> Unit) {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingDone by onboardingViewModel.onboardingDone.collectAsState()
    // Route chosen on the last onboarding step; applied once the NavHost below exists.
    var postOnboardingRoute by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    if (!onboardingDone) {
        OnboardingScreen(onFinished = { choice ->
            postOnboardingRoute = when (choice) {
                OnboardingChoice.MANUAL -> Screen.Wallet.route
                OnboardingChoice.IMPORT, OnboardingChoice.CONNECT -> Screen.Settings.route
                OnboardingChoice.SKIP -> null
            }
            onboardingViewModel.finish()
        })
        return
    }
    androidx.compose.runtime.LaunchedEffect(postOnboardingRoute) {
        val route = postOnboardingRoute ?: return@LaunchedEffect
        postOnboardingRoute = null
        navController.navigate(route) { launchSingleTop = true }
    }
    val pendingRoute by NavigationRequests.pendingRoute.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingRoute) {
        if (pendingRoute == NavigationRequests.OPEN_PURCHASE) {
            NavigationRequests.consumeRoute()
            navController.navigate(Screen.Optimize.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

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
                    onOpenCardValue = { navController.navigate(Screen.CardValue.route) },
                    onOpenReconcile = { navController.navigate(Screen.Reconcile.route) },
                    onOpenAirport = { navController.navigate(Screen.Airport.route) },
                )
            }
            composable(Screen.Benefits.route) {
                BenefitsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CardValue.route) {
                com.travelbenefits.app.ui.cardvalue.CardValueScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Loyalty.route) {
                LoyaltyScreen(onOpenSettings = { navController.navigate(Screen.Settings.route) })
            }
            composable(Screen.Trips.route) { TripsScreen(onOpenTrip = { id -> navController.navigate(Screen.TripDetail.route(id)) }) }
            composable(Screen.TripDetail.route, arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType })) {
                com.travelbenefits.app.ui.trips.TripDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Airport.route) {
                com.travelbenefits.app.ui.airport.AirportModeScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Reconcile.route) {
                com.travelbenefits.app.ui.reconcile.ReconcileScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Optimize.route) { OptimizeScreen() }
            composable(Screen.Wallet.route) { WalletScreen(onOpenCardValue = { navController.navigate(Screen.CardValue.route) }) }
            composable(Screen.Settings.route) {
                SettingsScreen(onLaunchGmailAuth = onLaunchGmailAuth, onLaunchPlaidLink = onLaunchPlaidLink, onBack = { navController.popBackStack() })
            }
        }
    }
}
