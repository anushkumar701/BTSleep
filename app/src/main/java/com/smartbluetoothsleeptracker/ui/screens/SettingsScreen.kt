package com.smartbluetoothsleeptracker.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.BuildConfig
import com.smartbluetoothsleeptracker.core.screen.BTCurfewDeviceAdmin
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.PermissionStatus
import com.smartbluetoothsleeptracker.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTermsOfService: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current

    // Refresh permissions when screen becomes visible or activity resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = 24.dp,
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
                Text("Configure SleepBT", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── TIMER ──────────────────────────────────────────────────────
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

        // ── PLAYBACK CONTROL ───────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Playback Control") }
        item {
            SettingToggle(
                icon = Icons.Rounded.VolumeDown,
                title = "Stop Playback on Timer End",
                subtitle = "Gradually fade volume to zero before disconnecting",
                checked = s.playbackStopEnabled,
                onCheckedChange = { viewModel.setPlaybackStop(it) }
            )
        }
        if (s.playbackStopEnabled) {
            item {
                SettingRow(
                    icon = Icons.Rounded.Speed,
                    title = "Fade-Out Duration",
                    subtitle = "${s.fadeOutDurationSeconds} seconds",
                    trailing = {
                        Slider(
                            value = s.fadeOutDurationSeconds.toFloat(),
                            onValueChange = { viewModel.setFadeOutDuration(it.toInt()) },
                            valueRange = 3f..30f,
                            steps = 26,
                            modifier = Modifier.width(140.dp),
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                        )
                    }
                )
            }
            item {
                InfoCard(
                    "During the fade, pressing a volume key will cancel the fade, restore volume, " +
                    "and extend the timer instead of disconnecting."
                )
            }
        }

        // ── BLUETOOTH ──────────────────────────────────────────────────
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


        // ── SCREEN OFF ─────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Screen") }
        item {
            SettingToggle(
                icon = Icons.Rounded.ScreenLockPortrait,
                title = "Lock Screen on Timer End",
                subtitle = if (state.deviceAdminActive) "Device Admin active" else "Requires Device Admin",
                checked = s.screenOffEnabled,
                onCheckedChange = { viewModel.setScreenOff(it) }
            )
        }
        if (s.screenOffEnabled && !state.deviceAdminActive) {
            item {
                EnableButton("Enable Device Admin") {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            BTCurfewDeviceAdmin.componentName(context))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "SleepBT needs Device Admin to lock the screen when the sleep timer ends.")
                    }
                    context.startActivity(intent)
                }
            }
        }

        // ── HAPTIC FEEDBACK ────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Feedback") }
        item {
            SettingToggle(
                icon = Icons.Rounded.Vibration,
                title = "Haptic Feedback",
                subtitle = "Dial rotation ticks and button press vibration",
                checked = s.hapticFeedbackEnabled,
                onCheckedChange = { viewModel.setHapticFeedback(it) }
            )
        }

        // ── NOTIFICATIONS ──────────────────────────────────────────────
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

        // ── SERVICE ────────────────────────────────────────────────────
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

        // ── APPEARANCE ─────────────────────────────────────────────────
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

        // ── PERMISSIONS & ACCESS STATUS ────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Permissions & Access") }
        items(state.permissions, key = { it.name }) { perm ->
            PermissionRow(perm = perm, context = context)
        }

        // ── LEGAL ──────────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Legal") }
        item {
            SettingNavRow(
                icon = Icons.Rounded.PrivacyTip,
                title = "Privacy Policy",
                onClick = onNavigateToPrivacyPolicy
            )
        }
        item {
            SettingNavRow(
                icon = Icons.Rounded.Gavel,
                title = "Terms of Service",
                onClick = onNavigateToTermsOfService
            )
        }

        // ── ABOUT ──────────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("About") }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "SleepBT Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("SleepBT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceBorder)
                Spacer(Modifier.height(12.dp))
                Text(
                    "© 2026 DreamSync. All rights reserved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Composable Building Blocks ─────────────────────────────────────────

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

@Composable
private fun SettingNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PermissionRow(perm: PermissionStatus, context: Context) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            Modifier.size(10.dp).background(
                if (perm.granted) StatusGreen else StatusRed, CircleShape
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(perm.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(perm.description, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        if (perm.granted) {
            Text("Granted", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = StatusGreen)
        } else if (perm.settingsAction != null) {
            Box(
                Modifier
                    .background(AccentBlue.copy(0.12f), RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            val intent = Intent(perm.settingsAction).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (perm.settingsUri != null) {
                                    data = Uri.parse(perm.settingsUri)
                                }
                                // Special case for notification settings
                                if (perm.settingsAction == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback to app details
                            val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Enable", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = AccentBlue)
            }
        }
    }
}

@Composable
private fun EnableButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(StatusOrange.copy(0.08f), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AdminPanelSettings, null, tint = StatusOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = StatusOrange)
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        color = AccentBlue.copy(0.06f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, tint = AccentBlue.copy(0.6f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
