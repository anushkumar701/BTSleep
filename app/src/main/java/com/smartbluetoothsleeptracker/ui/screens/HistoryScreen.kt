package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.data.db.SessionEntity
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.DeviceStat
import com.smartbluetoothsleeptracker.viewmodel.HistoryTab
import com.smartbluetoothsleeptracker.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = SpaceSurfaceHigh,
            title = { Text("Clear All History?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("This permanently deletes all session records.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearAllDialog = false }) {
                    Text("Clear All", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DeepSpace),
        contentPadding = PaddingValues(
            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 20.dp,
            bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            start = 24.dp, end = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("History", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Your Bluetooth session log",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (state.totalSessions > 0) {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.background(ErrorRed.copy(0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, "Clear all", tint = ErrorRed)
                    }
                }
            }
        }

        // ── 7-Day Bar Chart ──────────────────────────────────────────────────
        item {
            WeeklyBarChart(allSessions = state.sessions)
        }

        // ── Tab selector ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(SpaceSurface, RoundedCornerShape(16.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryTab.values().forEach { tab ->
                    val selected = state.selectedTab == tab
                    val label = when (tab) {
                        HistoryTab.TODAY -> "Today"
                        HistoryTab.WEEK  -> "Week"
                        HistoryTab.MONTH -> "Month"
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(
                                if (selected) AccentBlue else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setTab(tab) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) Color.White else TextSecondary)
                    }
                }
            }
        }

        // ── Stats strip ─────────────────────────────────────────────────────
        item {
            val totalForTab = when (state.selectedTab) {
                HistoryTab.TODAY -> state.todayTotal
                HistoryTab.WEEK  -> state.weekTotal
                HistoryTab.MONTH -> state.monthTotal
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1.2f), "Total Time", formatDuration(totalForTab))
                StatCard(Modifier.weight(1f), "Sessions", "${state.sessions.size}")
                StatCard(Modifier.weight(1f), "Devices", "${state.deviceStats.size}")
            }
        }

        // ── Empty state ──────────────────────────────────────────────────────
        if (state.sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                        .background(SpaceSurface, RoundedCornerShape(24.dp))
                        .padding(vertical = 56.dp, horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.History, null, tint = AccentBlue.copy(0.3f),
                            modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No sessions in this period", color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Your sleep timer history will appear here.", color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            // ── By Device ───────────────────────────────────────────────────
            item {
                Text("DEVICES", style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp)
            }
            items(state.deviceStats, key = { "dev_" + it.deviceName }) { stat ->
                DeviceCard(stat = stat, onClick = { onDeviceClick(stat.deviceName) })
            }

            // ── Recent Sessions ──────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text("RECENT SESSIONS", style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp)
            }
            items(state.sessions, key = { it.id }) { session ->
                SessionRow(session, onDelete = { viewModel.deleteSession(session.id) })
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .background(SpaceSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

// ── Weekly Bar Chart ────────────────────────────────────────────────────────────

@Composable
private fun WeeklyBarChart(allSessions: List<SessionEntity>) {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    // Build 7-day data
    val days = (6 downTo 0).map { ago ->
        val d = today.minusDays(ago.toLong())
        val dateStr = d.format(fmt)
        val minutes = allSessions.filter { it.date == dateStr }.sumOf { it.duration / 60_000L }.toInt()
        val label = when (ago) {
            0 -> "Today"
            else -> d.dayOfWeek.name.take(1) + d.dayOfWeek.name.drop(1).take(2).lowercase()
        }
        Triple(label, minutes, d == today)
    }
    val maxMin = days.maxOf { it.second }.coerceAtLeast(60)

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("${days.sumOf { it.second }}m total",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { (label, minutes, isToday) ->
                val frac = (minutes / maxMin.toFloat()).coerceIn(0.04f, 1f)
                val barColor = when {
                    minutes > 120 -> ErrorRed
                    minutes > 60  -> Warning
                    else          -> AccentBlue
                }
                val animFrac by animateFloatAsState(frac,
                    animationSpec = tween(800, easing = FastOutSlowInEasing), label = "bar")

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Value label
                    Text(if (minutes > 0) "${minutes}m" else "",
                        style = MaterialTheme.typography.labelSmall, color = barColor,
                        fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    // Bar
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height((100 * animFrac).dp)
                            .background(
                                Brush.verticalGradient(listOf(barColor, barColor.copy(0.4f))),
                                RoundedCornerShape(8.dp)
                            )
                    )
                    Spacer(Modifier.height(8.dp))
                    // Day label
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) AccentBlue else TextTertiary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(AccentBlue, "≤ 1h")
            LegendDot(Warning, "1–2h")
            LegendDot(ErrorRed, "> 2h")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

// ── Device Card (tappable → opens DeviceDetail) ─────────────────────────────────

@Composable
private fun DeviceCard(stat: DeviceStat, onClick: () -> Unit) {
    val maxPossible = 8 * 60 * 60_000L
    val frac = (stat.totalDuration / maxPossible.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(AccentBlue.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Headphones, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stat.deviceName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text("${stat.sessionCount} sessions · ${formatDuration(stat.totalDuration)}",
                style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(SpaceSurface2, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).background(AccentBlue, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(24.dp))
    }
}

// ── Session row with explicit delete button ─────────────────────────────────────────

@Composable
private fun SessionRow(session: SessionEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(SpaceSurface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bluetooth, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(session.deviceName, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(formatDate(session.startTime), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(session.duration), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black, color = AccentBlue)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
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
