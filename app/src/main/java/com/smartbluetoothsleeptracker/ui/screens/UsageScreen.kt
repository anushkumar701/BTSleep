package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.data.db.DeviceType
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.DeviceUsageStat
import com.smartbluetoothsleeptracker.viewmodel.UsagePeriod
import com.smartbluetoothsleeptracker.viewmodel.UsageViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UsageScreen(
    viewModel: UsageViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedDevice by remember { mutableStateOf<DeviceUsageStat?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Surface2,
            title = { Text("Clear All Usage?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This permanently deletes all sessions and usage data.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("Clear All", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Device bottom sheet
    if (selectedDevice != null) {
        DeviceBottomSheet(
            stat = selectedDevice!!,
            onDismiss = { selectedDevice = null },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onResetUsage = { viewModel.resetUsageForDevice(it); selectedDevice = null },
            onRemoveDevice = { viewModel.removeDevice(it); selectedDevice = null },
            onSetType = { addr, type -> viewModel.setDeviceType(addr, type) }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            start = 24.dp, end = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Usage", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black, color = TextPrimary)
                    Text("Track your listening patterns", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (state.totalSessions > 0) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.background(StatusRed.copy(0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, null, tint = StatusRed)
                    }
                }
            }
        }

        // Period selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UsagePeriod.entries.forEach { p ->
                    val selected = state.period == p
                    Box(
                        Modifier.weight(1f)
                            .background(if (selected) AccentBlue else Color.Transparent, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewModel.setPeriod(p) }
                            .padding(vertical = 12.dp),
                        Alignment.Center
                    ) {
                        Text(
                            when (p) {
                                UsagePeriod.TODAY -> "Today"
                                UsagePeriod.WEEK -> "Week"
                                UsagePeriod.MONTH -> "Month"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        // 7-day bar chart
        item { WeeklyChart(state.dailyUsage) }

        // Stats row
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "Total", formatDuration(state.totalMinutes.toLong() * 60_000L))
                StatCard(Modifier.weight(1f), "Sessions", "${state.totalSessions}")
                StatCard(Modifier.weight(1f), "Devices", "${state.totalDevices}")
            }
        }

        // Device list
        if (state.deviceStats.isNotEmpty()) {
            item {
                Text("DEVICES", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)
            }
            items(state.deviceStats, key = { it.device.address }) { stat ->
                DeviceRow(stat = stat, onClick = { selectedDevice = stat })
            }
        }

        // Sessions
        if (state.sessions.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Text("SESSIONS", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)
            }
            items(state.sessions.take(20), key = { it.id }) { session ->
                SessionRow(session = session, onDelete = { viewModel.deleteSession(session.id) })
            }
        }

        // Empty
        if (state.sessions.isEmpty() && state.deviceStats.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 24.dp)
                        .background(Surface1, RoundedCornerShape(20.dp))
                        .padding(48.dp),
                    Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Analytics, null, tint = AccentBlue.copy(0.3f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No usage data yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Start a sleep timer to begin tracking", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String) {
    Column(
        modifier.background(Surface1, RoundedCornerShape(14.dp)).padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun WeeklyChart(dailyUsage: List<com.smartbluetoothsleeptracker.data.db.DailyUsageEntity>) {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    val days = (6 downTo 0).map { ago ->
        val d = today.minusDays(ago.toLong())
        val dateStr = d.format(fmt)
        val mins = dailyUsage.filter { it.date == dateStr }.sumOf { it.totalMinutes }
        val label = if (ago == 0) "Today" else d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        Triple(label, mins, ago == 0)
    }
    val maxMin = days.maxOf { it.second }.coerceAtLeast(60)

    Column(
        Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(20.dp)).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${days.sumOf { it.second }}m", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
            days.forEach { (label, minutes, isToday) ->
                val frac = (minutes / maxMin.toFloat()).coerceIn(0.04f, 1f)
                val color = when { minutes > 120 -> StatusRed; minutes > 60 -> StatusOrange; else -> AccentBlue }
                val animFrac by animateFloatAsState(frac, tween(800, easing = FastOutSlowInEasing), label = "b")

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (minutes > 0) "${minutes}m" else "",
                        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(30.dp).height((90 * animFrac).dp)
                            .background(Brush.verticalGradient(listOf(color, color.copy(0.4f))), RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) AccentBlue else TextTertiary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(stat: DeviceUsageStat, onClick: () -> Unit) {
    val icon = when (stat.device.deviceType) {
        DeviceType.EARBUDS -> Icons.Rounded.Headphones
        DeviceType.NECKBAND -> Icons.Rounded.Headphones
        DeviceType.HOME_THEATRE -> Icons.Rounded.Speaker
        DeviceType.PC -> Icons.Rounded.Computer
        DeviceType.SMARTWATCH -> Icons.Rounded.Watch
        DeviceType.OTHER -> Icons.Rounded.Bluetooth
    }

    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(AccentBlue.copy(0.1f), CircleShape), Alignment.Center) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (stat.device.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Star, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                }
            }
            Text("${stat.sessionCount} sessions · ${formatDuration(stat.totalMinutes.toLong() * 60_000L)}",
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.deviceName.ifEmpty { session.deviceAddress },
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Row {
                Text(
                    formatDate(session.startTime),
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary
                )
                if (session.actualDurationMin != null) {
                    Text(
                        " · ${session.actualDurationMin}m / ${session.plannedDurationMin}m",
                        style = MaterialTheme.typography.labelSmall, color = TextTertiary
                    )
                }
            }
        }
        if (session.disconnectConfirmed) {
            Icon(Icons.Rounded.CheckCircle, "Confirmed", tint = StatusGreen, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Rounded.Warning, "Unconfirmed", tint = StatusOrange, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, "Delete", tint = TextTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceBottomSheet(
    stat: DeviceUsageStat,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onResetUsage: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onSetType: (String, DeviceType) -> Unit
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Surface2,
            title = { Text("Reset Usage?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Clear all usage data for ${stat.device.name}?", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { onResetUsage(stat.device.address); showResetConfirm = false }) { Text("Reset", color = StatusRed) } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            containerColor = Surface2,
            title = { Text("Remove Device?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Remove ${stat.device.name} and all its data?", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { onRemoveDevice(stat.device.address); showRemoveConfirm = false }) { Text("Remove", color = StatusRed) } },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface2
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp)) {
            Text(stat.device.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${stat.sessionCount} sessions · ${formatDuration(stat.totalMinutes.toLong() * 60_000L)}", color = TextSecondary)
            Spacer(Modifier.height(20.dp))

            // Device type selector
            Text("Device Type", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                DeviceType.entries.take(4).forEach { type ->
                    val selected = stat.device.deviceType == type
                    Box(
                        Modifier.background(if (selected) AccentBlue else Surface3, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSetType(stat.device.address, type) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) TextOnAccent else TextSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(Modifier.height(12.dp))

            // Actions
            BottomSheetAction(Icons.Rounded.Star, if (stat.device.isFavorite) "Remove from favorites" else "Add to favorites", AccentBlue) {
                onToggleFavorite(stat.device.address)
            }
            BottomSheetAction(Icons.Rounded.Refresh, "Reset usage data", StatusOrange) { showResetConfirm = true }
            BottomSheetAction(Icons.Rounded.Delete, "Remove device", StatusRed) { showRemoveConfirm = true }
        }
    }
}

@Composable
private fun BottomSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val m = millis / 60_000L; val h = m / 60
    return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
}

private fun formatDate(ms: Long): String = runCatching {
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(ms))
}.getOrDefault("—")
