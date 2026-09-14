package com.consensus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.consensus.app.ui.settings.SettingsScreen
import com.consensus.app.ui.thread.ThreadScreen
import com.consensus.app.ui.threads.ThreadListScreen

object Routes {
    const val THREADS = "threads"
    const val SETTINGS = "settings"
    const val THREAD = "thread/{id}"
    fun thread(id: Long) = "thread/$id"
    /** id 0 = start a new thread on the first question. */
    const val NEW_THREAD_ID = 0L
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.THREADS) {
        composable(Routes.THREADS) {
            ThreadListScreen(
                onOpenThread = { id -> nav.navigate(Routes.thread(id)) },
                onNewThread = { nav.navigate(Routes.thread(Routes.NEW_THREAD_ID)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.THREAD,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            ThreadScreen(
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
