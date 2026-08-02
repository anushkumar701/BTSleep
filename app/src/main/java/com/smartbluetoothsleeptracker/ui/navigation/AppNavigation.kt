package com.smartbluetoothsleeptracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartbluetoothsleeptracker.ui.screens.*
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.*

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Rounded.NightsStay)
    data object Usage : Tab("usage", "Usage", Icons.Rounded.BarChart)
    data object Health : Tab("health", "Health", Icons.Rounded.Favorite)
    data object Settings : Tab("settings", "Settings", Icons.Rounded.Settings)
}

val tabs = listOf(Tab.Home, Tab.Usage, Tab.Health, Tab.Settings)

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = Surface1,
                contentColor = TextPrimary,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(tab.icon, tab.label, modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            unselectedIconColor = TextTertiary,
                            unselectedTextColor = TextTertiary,
                            indicatorColor = AccentBlue.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.Home.route) {
                HomeScreen(viewModel = homeViewModel)
            }
            composable(Tab.Usage.route) {
                val usageVm: UsageViewModel = viewModel()
                UsageScreen(viewModel = usageVm)
            }
            composable(Tab.Health.route) {
                val healthVm: HealthViewModel = viewModel()
                HealthScreen(viewModel = healthVm)
            }
            composable(Tab.Settings.route) {
                val settingsVm: SettingsViewModel = viewModel()
                SettingsScreen(viewModel = settingsVm)
            }
        }
    }
}
