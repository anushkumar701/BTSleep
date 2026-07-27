package com.smartbluetoothsleeptracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartbluetoothsleeptracker.ui.screens.*
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.*
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Sleep",   Icons.Rounded.Bedtime)
    object History  : Screen("history",  "History", Icons.Rounded.BarChart)
    object Health   : Screen("health",   "Health",  Icons.Rounded.HealthAndSafety)
    object Settings : Screen("settings", "Settings",Icons.Rounded.Settings)
}

val SCREENS = listOf(Screen.Home, Screen.History, Screen.Health, Screen.Settings)

@Composable
fun AppNavigation(
    homeVm: HomeViewModel,
    historyVm: HistoryViewModel,
    settingsVm: SettingsViewModel,
    healthVm: HealthViewModel,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onExtendTimer: () -> Unit,
    onDisconnectNow: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = DeepSpace,
        bottomBar = {
            val navBackStack by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStack?.destination?.route
            val isDetailPage = currentRoute?.startsWith("device_detail/") == true

            if (!isDetailPage) {
                NavigationBar(
                    containerColor = SpaceSurface,
                    tonalElevation = 0.dp
                ) {
                    val currentDest = navBackStack?.destination
                    SCREENS.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon,
                                    screen.label,
                                    tint = if (selected) AccentBlue else TextTertiary
                                )
                            },
                            label = {
                                Text(
                                    screen.label,
                                    color = if (selected) AccentBlue else TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                indicatorColor = AccentBlue.copy(alpha = 0.16f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    homeVm = homeVm,
                    onStartTimer = onStartTimer,
                    onCancelTimer = onCancelTimer,
                    onExtendTimer = onExtendTimer,
                    onDisconnectNow = onDisconnectNow
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = historyVm,
                    onDeviceClick = { deviceName ->
                        val encoded = URLEncoder.encode(deviceName, "UTF-8")
                        navController.navigate("device_detail/$encoded")
                    }
                )
            }
            composable(Screen.Health.route) {
                HealthScreen(viewModel = healthVm)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsVm)
            }

            composable(
                route = "device_detail/{deviceName}",
                arguments = listOf(navArgument("deviceName") { type = NavType.StringType })
            ) { backStack ->
                val encodedName = backStack.arguments?.getString("deviceName") ?: return@composable
                val deviceName = URLDecoder.decode(encodedName, "UTF-8")
                val state by historyVm.state.collectAsState()
                val stat = state.deviceStats.find { it.deviceName == deviceName }

                if (stat != null) {
                    DeviceDetailScreen(
                        stat = stat,
                        onResetTiming = { historyVm.resetDeviceTiming(it) },
                        onRemoveDevice = { historyVm.deleteDeviceHistory(it) },
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    navController.popBackStack()
                }
            }
        }
    }
}
