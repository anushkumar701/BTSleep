package com.smartbluetoothsleeptracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

// Non-tab routes (no bottom bar)
object Routes {
    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_OF_SERVICE = "terms_of_service"
}

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Only show bottom bar on main tabs
    val showBottomBar = currentRoute in tabs.map { it.route }

    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (showBottomBar && homeState.isTimerRunning && currentRoute != Tab.Home.route) {
                    val remSec = (homeState.remainingMs / 1000L).coerceAtLeast(0L)
                    val m = remSec / 60
                    val s = remSec % 60
                    val timeStr = String.format("%02d:%02d", m, s)

                    Surface(
                        color = Surface2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate(Tab.Home.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (homeState.isPaused) StatusOrange else StatusGreen, androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (homeState.isPaused) "Sleep Timer Paused" else "Sleep Timer Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = AccentBlue
                            )
                        }
                    }
                }

                if (showBottomBar) {
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
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Main Tabs ──────────────────────────────────────
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
                SettingsScreen(
                    viewModel = settingsVm,
                    onNavigateToPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                    onNavigateToTermsOfService = { navController.navigate(Routes.TERMS_OF_SERVICE) }
                )
            }

            // ── Legal Screens (no bottom bar) ──────────────────
            composable(Routes.PRIVACY_POLICY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TERMS_OF_SERVICE) {
                TermsOfServiceScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
