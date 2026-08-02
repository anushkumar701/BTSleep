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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.BuildConfig
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            start = 24.dp, end = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Column {
                Text("Settings", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Configure BT Curfew", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Timer ──────────────────────────────────────────────────────
        item { SectionHeader("Timer") }
        item {
            SettingRow(
                icon = Icons.Rounded.Timer,
                title = "Extend Duration",
                subtitle = "${s.extendMinutes} minutes per tap",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setExtendMinutes(s.extendMinutes - 1) }, Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Remove, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        Text("${s.extendMinutes}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = { viewModel.setExtendMinutes(s.extendMinutes + 1) }, Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Add, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
        }

        // ── Bluetooth ──────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Bluetooth") }
        item {
            SettingToggle(
                icon = Icons.Rounded.Shield,
                title = "Reconnect Blocker",
                subtitle = "Block device from reconnecting after timer ends",
                checked = s.reconnectBlockerEnabled,
                onCheckedChange = { viewModel.setReconnectBlocker(it) }
            )
        }
        if (s.reconnectBlockerEnabled) {
            item {
                SettingRow(
                    icon = Icons.Rounded.Timelapse,
                    title = "Cooldown Window",
                    subtitle = "${s.cooldownSeconds} seconds",
                    trailing = {
                        Slider(
                            value = s.cooldownSeconds.toFloat(),
                            onValueChange = { viewModel.setCooldownSeconds(it.toInt()) },
                            valueRange = 0f..120f,
                            steps = 11,
                            modifier = Modifier.width(140.dp),
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                        )
                    }
                )
            }
        }
        item {
            SettingRow(
                icon = Icons.Rounded.Memory,
                title = "Shizuku",
                subtitle = if (state.shizukuAvailable) "Connected — privileged control available" else "Not available",
                trailing = {
                    Box(
                        Modifier.size(10.dp).background(
                            if (state.shizukuAvailable) StatusGreen else TextTertiary, CircleShape
                        )
                    )
                }
            )
        }

        // ── Notifications ──────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Notifications") }
        item {
            SettingToggle(
                icon = Icons.Rounded.Notifications,
                title = "Sleep Alerts",
                subtitle = "Notify before timer ends",
                checked = s.sleepAlertsEnabled,
                onCheckedChange = { viewModel.setSleepAlerts(it) }
            )
        }
        if (s.sleepAlertsEnabled) {
            item {
                SettingRow(
                    icon = Icons.Rounded.Schedule,
                    title = "Warning Lead Time",
                    subtitle = "${s.warningLeadMinutes} minutes before end",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setWarningLeadMinutes(s.warningLeadMinutes - 1) }, Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Remove, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            Text("${s.warningLeadMinutes}", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = TextPrimary)
                            IconButton(onClick = { viewModel.setWarningLeadMinutes(s.warningLeadMinutes + 1) }, Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Add, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
            }
        }

        // ── Service ────────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Service") }
        item {
            SettingToggle(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Keep Alive",
                subtitle = "Timer runs in background when app is closed",
                checked = s.foregroundServiceEnabled,
                onCheckedChange = { viewModel.setForegroundService(it) }
            )
        }

        // ── Appearance ─────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Appearance") }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(16.dp))
                    .padding(6.dp),
                Arrangement.spacedBy(4.dp)
            ) {
                listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "System").forEach { (mode, label) ->
                    val selected = s.themeMode == mode
                    Box(
                        Modifier.weight(1f)
                            .background(if (selected) AccentBlue else Color.Transparent, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(vertical = 12.dp),
                        Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextSecondary)
                    }
                }
            }
        }

        // ── About ──────────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("About") }
        item {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "BT Curfew",
                subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                trailing = {}
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = TextTertiary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        trailing()
    }
}

@Composable
private fun SettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = Surface3
            )
        )
    }
}
