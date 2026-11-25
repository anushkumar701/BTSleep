package com.smartbluetoothsleeptracker.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.BuildConfig
import com.smartbluetoothsleeptracker.core.screen.SleepBTDeviceAdmin
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.PermissionStatus
import com.smartbluetoothsleeptracker.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToTermsOfService: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current

    var isAutoStartExempt by remember {
        mutableStateOf(com.smartbluetoothsleeptracker.core.util.AutoStartHelper.isAutoStartOrBatteryOptimizationExempt(context))
    }

    // Refresh permissions when screen becomes visible or activity resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
                isAutoStartExempt = com.smartbluetoothsleeptracker.core.util.AutoStartHelper.isAutoStartOrBatteryOptimizationExempt(context)
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
                            SleepBTDeviceAdmin.componentName(context))
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
                subtitle = "Dial rotation ticks for smooth mechanical ratchet feel",
                checked = s.hapticFeedbackEnabled,
                onCheckedChange = { viewModel.setHapticFeedback(it) }
            )
        }
        if (s.hapticFeedbackEnabled) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            com.smartbluetoothsleeptracker.core.haptics.HapticManager.testVibration(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Vibration, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Vibration", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── NOTIFICATIONS ──────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Notifications") }
        item {
            SettingToggle(
                icon = Icons.Rounded.BatteryChargingFull,
                title = "Connection Battery Alert",
                subtitle = "Show notification with battery percentage when audio device connects",
                checked = s.connectBatteryNotificationEnabled,
                onCheckedChange = { viewModel.setConnectBatteryNotification(it) }
            )
        }
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

        // ── SERVICE & BACKGROUND ─────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Service & Background") }
        item {
            SettingToggle(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Keep Alive",
                subtitle = "Timer runs in background when app is closed",
                checked = s.foregroundServiceEnabled,
                onCheckedChange = { viewModel.setForegroundService(it) }
            )
        }
        item {
            SettingNavRow(
                icon = Icons.Rounded.Bolt,
                title = "Auto-Start & Background Activity",
                subtitle = if (isAutoStartExempt) "Exempt from battery saver • Background activity enabled" else "Allow SleepBT in system battery settings so sleep timers never get killed",
                trailingBadge = {
                    if (isAutoStartExempt) {
                        Box(
                            modifier = Modifier
                                .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Enabled ✓", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(StatusOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Tap to Enable", color = StatusOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                },
                onClick = {
                    com.smartbluetoothsleeptracker.core.util.AutoStartHelper.openAutoStartSettings(context)
                }
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

        // ── UPDATES ───────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Updates") }
        item {
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            val updateManager = remember { com.smartbluetoothsleeptracker.core.update.UpdateManager(context) }

            SettingNavRow(
                icon = Icons.Rounded.SystemUpdate,
                title = if (checking) "Checking for updates..." else "Check for Updates",
                onClick = {
                    scope.launch {
                        checking = true
                        updateManager.checkForUpdates(force = true)
                        checking = false
                        val info = updateManager.updateState.value
                        if (info.isAvailable) {
                            updateManager.openReleaseInBrowser(info.downloadUrl)
                        } else {
                            android.widget.Toast.makeText(context, "SleepBT is up to date (v${com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME})", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // ── HELP & GUIDE ────────────────────────────────────────────────
        item { Spacer(Modifier.height(8.dp)); SectionHeader("Help & Guide") }
        item {
            SettingNavRow(
                icon = Icons.Rounded.School,
                title = "App Guide & Interactive Tutorial",
                subtitle = "Launch the hands-on guided tour on the Home screen",
                onClick = {
                    viewModel.setTutorialCompleted(false)
                    onNavigateToHome()
                }
            )
        }
        item {
            SettingNavRow(
                icon = Icons.Rounded.Email,
                title = "Feedback & Queries",
                subtitle = "Contact the developer directly",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:midnightcompiler01@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, "SleepBT Feedback & Queries (v${com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME})")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("midnightcompiler01@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "SleepBT Feedback & Queries")
                        }
                        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Send Feedback")) }
                    }
                }
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
                        Text("Version ${com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SurfaceBorder)
                Spacer(Modifier.height(12.dp))
                Text(
                    "For feedback & queries: midnightcompiler01@gmail.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentBlue,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:midnightcompiler01@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "SleepBT Feedback & Queries (v${com.smartbluetoothsleeptracker.BuildConfig.VERSION_NAME})")
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "© 2026 Midnight Compiler. All rights reserved.",
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
            onCheckedChange = { newValue ->
                onCheckedChange(newValue)
            },
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
    subtitle: String? = null,
    trailingBadge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
        if (trailingBadge != null) {
            trailingBadge()
            Spacer(Modifier.width(8.dp))
        }
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
