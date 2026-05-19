package com.flightcompare.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flightcompare.ui.screens.alerts.AlertsScreen
import com.flightcompare.ui.screens.bookmarks.BookmarksScreen
import com.flightcompare.ui.screens.detail.DetailScreen
import com.flightcompare.ui.screens.history.HistoryScreen
import com.flightcompare.ui.screens.results.ResultsScreen
import com.flightcompare.ui.screens.search.SearchScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Route.Search.route, "Search", Icons.Default.Search),
    BottomNavItem(Route.Bookmarks.route, "Bookmarks", Icons.Default.Bookmark),
    BottomNavItem(Route.Alerts.route, "Alerts", Icons.Default.Notifications),
)

@Composable
fun FlightCompareNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDest?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Search.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Route.Search.route) {
                SearchScreen(
                    onSearch = { searchId ->
                        navController.navigate(Route.Results.create(searchId))
                    }
                )
            }

            composable(
                Route.Results.route,
                arguments = listOf(navArgument("searchId") { type = NavType.StringType })
            ) { backStackEntry ->
                val searchId = backStackEntry.arguments?.getString("searchId") ?: return@composable
                ResultsScreen(
                    searchId = searchId,
                    onFlightClick = { flightId ->
                        navController.navigate(Route.Detail.create(flightId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Route.Detail.route,
                arguments = listOf(navArgument("flightId") { type = NavType.StringType })
            ) { backStackEntry ->
                val flightId = backStackEntry.arguments?.getString("flightId") ?: return@composable
                DetailScreen(
                    flightId = flightId,
                    onViewHistory = { fid ->
                        navController.navigate(Route.History.create(fid))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Route.History.route,
                arguments = listOf(navArgument("flightId") { type = NavType.StringType })
            ) { backStackEntry ->
                val flightId = backStackEntry.arguments?.getString("flightId") ?: return@composable
                HistoryScreen(
                    flightId = flightId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Route.Bookmarks.route) {
                BookmarksScreen(
                    onFlightClick = { flightId ->
                        navController.navigate(Route.Detail.create(flightId))
                    }
                )
            }

            composable(Route.Alerts.route) {
                AlertsScreen(
                    onFlightClick = { flightId ->
                        navController.navigate(Route.Detail.create(flightId))
                    }
                )
            }
        }
    }
}
