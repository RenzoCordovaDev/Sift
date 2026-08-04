package com.callbloqued.sift.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.callbloqued.sift.R
import com.callbloqued.sift.presentation.history.HistoryScreen
import com.callbloqued.sift.presentation.manuallists.ManualListsScreen
import com.callbloqued.sift.presentation.settings.SettingsScreen

/**
 * Root composable that owns the [NavHostController] and lays out the app-level scaffold.
 *
 * Renders a [Scaffold] with a [NavigationBar] at the bottom and a [NavHost] in the body.
 * The three destinations ([SiftDestination.HISTORY], [SiftDestination.SETTINGS],
 * [SiftDestination.MANUAL_LISTS]) are top-level peers navigated via the bottom bar.
 */
@Composable
fun SiftNavHost() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { SiftBottomBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SiftDestination.HISTORY.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(SiftDestination.HISTORY.route) { HistoryScreen() }
            composable(SiftDestination.SETTINGS.route) { SettingsScreen() }
            composable(SiftDestination.MANUAL_LISTS.route) { ManualListsScreen() }
        }
    }
}

/**
 * Bottom navigation bar that reflects the current destination and handles tab selection.
 *
 * Uses [NavHostController.navigate] with [NavGraph.findStartDestination] to implement the
 * standard single-top, save-state, restore-state pattern recommended by Jetpack Navigation.
 *
 * @param navController The [NavHostController] shared with the [NavHost] above.
 */
@Composable
private fun SiftBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        navBarItems().forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = { navigateTo(navController, item.destination) },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) }
            )
        }
    }
}

/**
 * Navigates to [destination] using the recommended single-top, save/restore state strategy.
 *
 * This avoids creating duplicate back-stack entries for top-level destinations and preserves
 * each tab's scroll/input state across switches.
 *
 * @param navController The controller managing the back stack.
 * @param destination The [SiftDestination] to navigate to.
 */
private fun navigateTo(navController: NavHostController, destination: SiftDestination) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Represents a single item in the bottom navigation bar.
 *
 * @property destination The [SiftDestination] this item navigates to.
 * @property icon The vector icon displayed in the tab.
 * @property label The human-readable tab label displayed below the icon.
 */
private data class NavBarItem(
    val destination: SiftDestination,
    val icon: ImageVector,
    val label: String
)

/**
 * Returns the ordered list of items to display in the bottom navigation bar.
 *
 * Called inside a composable so that string resources are resolved at render time.
 *
 * @return Ordered [List] of [NavBarItem] for the three top-level destinations.
 */
@Composable
private fun navBarItems(): List<NavBarItem> = listOf(
    NavBarItem(SiftDestination.HISTORY, Icons.Filled.Phone, stringResource(R.string.nav_history)),
    NavBarItem(SiftDestination.SETTINGS, Icons.Filled.Settings, stringResource(R.string.nav_settings)),
    NavBarItem(SiftDestination.MANUAL_LISTS, Icons.Filled.List, stringResource(R.string.nav_manual_lists))
)
