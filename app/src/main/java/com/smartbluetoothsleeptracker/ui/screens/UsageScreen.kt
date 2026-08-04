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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

@OptIn(ExperimentalMaterial3Api::class)
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
            onResetUsage = { viewModel.scheduleResetUsage(it); selectedDevice = null },
            onRemoveDevice = { viewModel.scheduleRemoveDevice(it); selectedDevice = null },
            onSetType = { addr, type -> viewModel.setDeviceType(addr, type) }
        )
    }

    val view = androidx.compose.ui.platform.LocalView.current

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = 20.dp,
                    bottom = 32.dp,
                    start = 24.dp, end = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column {
                        Text("Usage History", style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("Tracking bluetooth sleep timer sessions",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }

                // Period chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(14.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        UsagePeriod.entries.forEach { period ->
                            val selected = state.period == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (selected) AccentBlue else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        view.isHapticFeedbackEnabled = true
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                                        viewModel.setPeriod(period)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = period.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) TextOnAccent else TextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Bar Chart
                item {
                    DynamicBarChart(title = state.chartTitle, items = state.chartItems)
                }

                // Stats Cards
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

                // Enhanced empty state
                if (state.sessions.isEmpty() && state.deviceStats.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 24.dp)
                                .background(Surface1, RoundedCornerShape(20.dp))
                                .padding(48.dp),
                            Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier.size(80.dp)
                                            .background(AccentBlue.copy(0.08f), CircleShape)
                                    )
                                    Icon(
                                        Icons.Rounded.NightsStay, null,
                                        tint = AccentBlue.copy(0.35f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    "No usage data yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Start a sleep timer to begin tracking\nyour listening patterns",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } // end PullToRefreshBox

        // Undo Snackbar
        val undo = state.undoState
        if (undo.message != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.undoPendingAction() }) {
                        Text("UNDO", color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Surface2,
                contentColor = TextPrimary
            ) {
                Text(undo.message)
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
private fun DynamicBarChart(
    title: String,
    items: List<com.smartbluetoothsleeptracker.viewmodel.ChartBarItem>
) {
    if (items.isEmpty()) return

    val totalMinutes = items.sumOf { it.minutes }
    val maxMin = items.maxOf { it.minutes }.coerceAtLeast(60)

    Column(
        Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(24.dp)).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${formatMinutes(totalMinutes)} total", style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            // Dotted grid lines
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(formatMinutes(maxMin), formatMinutes(maxMin / 2), "0m").forEach { valLabel ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            valLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            fontSize = 8.sp,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Start
                        )
                        androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = SurfaceBorder,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 2f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                    }
                }
            }

            // Bars
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 40.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                items.forEach { item ->
                    val frac = (item.minutes / maxMin.toFloat()).coerceIn(0.02f, 1f)
                    val color = when {
                        item.minutes > 120 -> StatusRed
                        item.minutes > 60 -> StatusOrange
                        else -> AccentBlue
                    }
                    val animFrac by animateFloatAsState(
                        targetValue = frac,
                        animationSpec = tween(800, easing = FastOutSlowInEasing),
                        label = "barHeight"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (item.minutes > 0) {
                            Text(
                                formatMinutes(item.minutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(16.dp)
                                .height((80 * animFrac).dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(color, color.copy(0.3f))
                                    ),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                                )
                        )
                    }
                }
            }
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isHighlighted) AccentBlue else TextTertiary,
                    fontWeight = if (item.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        stat.device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stat.device.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    formatDuration(stat.totalMinutes.toLong() * 60_000L),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = AccentBlue
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${stat.sessionCount} sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                val isVerified = !stat.device.workingDisconnectMethod.isNullOrBlank()
                Text(
                    text = if (isVerified) "Verified" else "Not yet verified",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVerified) StatusGreen else TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(
                            if (isVerified) StatusGreen.copy(0.12f) else Surface2,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SessionRow(session: SessionEntity, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (session.disconnectConfirmed) StatusGreen.copy(0.12f) else Surface3,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (session.disconnectConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.BluetoothDisabled,
                contentDescription = null,
                tint = if (session.disconnectConfirmed) StatusGreen else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.deviceName.ifEmpty { session.deviceAddress },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(session.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        val playedMin = session.actualDurationMin ?: 0
        Box(
            Modifier
                .background(Surface3, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "Played: ${formatMinutes(playedMin)} · Set: ${formatMinutes(session.plannedDurationMin)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp).background(Surface3.copy(0.5f), CircleShape)
        ) {
            Icon(Icons.Rounded.Close, "Delete", tint = TextTertiary, modifier = Modifier.size(14.dp))
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
    return formatMinutes((millis / 60_000L).toInt())
}

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val remM = minutes % 60
    return when {
        h > 0 && remM > 0 -> "${h}h ${remM}m"
        h > 0 -> "${h}h"
        else -> "${remM}m"
    }
}

private fun formatDate(ms: Long): String = runCatching {
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(java.util.Date(ms))
}.getOrDefault("—")
