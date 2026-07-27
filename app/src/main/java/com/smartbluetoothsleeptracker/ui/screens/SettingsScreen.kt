package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace),
        contentPadding = PaddingValues(
            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            start = 24.dp, end = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Customize your experience", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        // Timer Configuration
        item {
            SettingsGroup(title = "Timer") {
                SettingsToggle(
                    icon = Icons.Rounded.Timer, title = "Extend Duration",
                    subtitle = "${s.extendMinutes} minutes per tap",
                    isSwitch = false,
                    onClick = {
                        val next = when (s.extendMinutes) { 5 -> 10; 10 -> 15; 15 -> 30; 30 -> 60; else -> 5 }
                        viewModel.setExtendMinutes(next)
                    }
                )
            }
        }

        // Bluetooth
        item {
            SettingsGroup(title = "Bluetooth") {
                SettingsToggle(
                    icon = Icons.Rounded.Block, title = "Reconnect Blocker",
                    subtitle = "Prevent device from reconnecting after disconnect",
                    isSwitch = true,
                    checked = s.reconnectBlockerEnabled,
                    onCheckedChange = viewModel::setReconnectBlocker
                )
                SettingsDivider()
                SettingsToggle(
                    icon = Icons.Rounded.BatterySaver, title = "Battery Saver",
                    subtitle = "Auto-disconnect after ${s.idleMinutes} min of audio idle",
                    isSwitch = true,
                    checked = s.batterySaverEnabled,
                    onCheckedChange = viewModel::setBatterySaver
                )
                if (s.batterySaverEnabled) {
                    SettingsDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Idle disconnect threshold", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("${s.idleMinutes} min", style = MaterialTheme.typography.bodyMedium, color = AccentBlue, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = s.idleMinutes.toFloat(),
                            onValueChange = { viewModel.setIdleMinutes(it.toInt()) },
                            onValueChangeFinished = {
                                // Snap to nearest 5-minute increment on release
                                val snapped = ((s.idleMinutes + 2) / 5) * 5
                                viewModel.setIdleMinutes(snapped.coerceIn(5, 60))
                            },
                            valueRange = 5f..60f,
                            steps = 10,
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = SpaceSurface2)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("5 min", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                            Text("60 min", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                    }
                }
            }
        }

        // Notifications
        item {
            SettingsGroup(title = "Notifications") {
                SettingsToggle(
                    icon = Icons.Rounded.Notifications, title = "Sleep Alerts",
                    subtitle = "2-minute warning before disconnect",
                    isSwitch = true,
                    checked = s.notificationsEnabled,
                    onCheckedChange = viewModel::setNotifications
                )
                SettingsDivider()
                SettingsToggle(
                    icon = Icons.Rounded.SettingsSuggest, title = "Foreground Service",
                    subtitle = "Keeps timer running when app is closed",
                    isSwitch = true,
                    checked = s.foregroundServiceEnabled,
                    onCheckedChange = viewModel::setForegroundService
                )
            }
        }

        // Appearance
        item {
            SettingsGroup(title = "Appearance") {
                SettingsToggle(
                    icon = Icons.Rounded.DarkMode, title = "Theme",
                    subtitle = when (s.themeMode) { "DARK" -> "Dark"; "LIGHT" -> "Light"; else -> "System / Auto" },
                    isSwitch = false,
                    onClick = {
                        val next = when (s.themeMode) { "DARK" -> "LIGHT"; "LIGHT" -> "AUTO"; else -> "DARK" }
                        viewModel.setThemeMode(next)
                    }
                )
            }
        }

        // About
        item {
            SettingsGroup(title = "About") {
                SettingsToggle(
                    icon = Icons.Rounded.Info, title = "SleepBT",
                    subtitle = "Version 2.0 — Smart Bluetooth Sleep Timer",
                    isSwitch = false, onClick = {}
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceSurface, RoundedCornerShape(20.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = SpaceSurfaceHigh,
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSwitch: Boolean,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isSwitch) ({ onCheckedChange(!checked) }) else onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(AccentBlue.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        if (isSwitch) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentBlue,
                    checkedTrackColor = AccentBlue.copy(0.25f),
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = SpaceSurface2,
                    uncheckedBorderColor = SpaceSurfaceHigh
                )
            )
        }
    }
}
