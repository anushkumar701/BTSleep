package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.smartbluetoothsleeptracker.ui.components.GlassCard
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
            containerColor = SpaceSurface,
            title = { Text("Clear All History?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("This permanently deletes all session records.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearAllDialog = false }) {
                    Text("Clear All", color = Color(0xFFFF4D6A), fontWeight = FontWeight.Bold)
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
            top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            start = 20.dp, end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("History", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Your Bluetooth session log",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (state.totalSessions > 0) {
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(Icons.Rounded.DeleteSweep, null, tint = TextTertiary)
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
                    .background(SpaceSurface, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryTab.values().forEach { tab ->
                    val selected = state.selectedTab == tab
                    val label = when (tab) {
                        HistoryTab.TODAY -> "Today"
                        HistoryTab.WEEK  -> "This Week"
                        HistoryTab.MONTH -> "This Month"
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                            .background(
                                if (selected) AccentBlue else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setTab(tab) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(modifier = Modifier.weight(1f).padding(0.dp)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDuration(totalForTab), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = AccentBlue)
                        Text("Total", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f).padding(0.dp)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.sessions.size}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = AccentBlue)
                        Text("Sessions", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f).padding(0.dp)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.deviceStats.size}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = AccentBlue)
                        Text("Devices", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }

        // ── Empty state ──────────────────────────────────────────────────────
        if (state.sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        .background(SpaceSurface, RoundedCornerShape(24.dp))
                        .padding(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Bedtime, null, tint = AccentBlue.copy(0.4f),
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No sessions in this period", color = TextSecondary,
                            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            // ── By Device ───────────────────────────────────────────────────
            item {
                Text("BY DEVICE", style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            items(state.deviceStats, key = { it.deviceName }) { stat ->
                DeviceCard(stat = stat, onClick = { onDeviceClick(stat.deviceName) })
            }

            // ── Recent Sessions ──────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Text("RECENT SESSIONS", style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
            items(state.sessions, key = { it.id }) { session ->
                SwipeToDeleteBox(onDelete = { viewModel.deleteSession(session.id) }) {
                    SessionRow(session)
                }
            }
        }
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
            .background(SpaceSurface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Last 7 Days", style = MaterialTheme.typography.titleSmall,
                color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("${days.sumOf { it.second }}m total",
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { (label, minutes, isToday) ->
                val frac = (minutes / maxMin.toFloat()).coerceIn(0.05f, 1f)
                val barColor = when {
                    minutes > 120 -> Color(0xFFFF4D6A)
                    minutes > 60  -> Color(0xFFFFB300)
                    else          -> AccentBlue
                }
                val animFrac by animateFloatAsState(frac,
                    animationSpec = tween(800, easing = FastOutSlowInEasing), label = "bar")

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Value label
                    Text(if (minutes > 0) "${minutes}m" else "",
                        style = MaterialTheme.typography.labelSmall, color = barColor,
                        fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    // Bar
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height((88 * animFrac).dp)
                            .background(
                                Brush.verticalGradient(listOf(barColor, barColor.copy(0.6f))),
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                    )
                    Spacer(Modifier.height(5.dp))
                    // Day label
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) AccentBlue else TextTertiary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(AccentBlue, "≤ 1h")
            LegendDot(Color(0xFFFFB300), "1–2h")
            LegendDot(Color(0xFFFF4D6A), "> 2h")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
    }
}

// ── Device Card (tappable → opens DeviceDetail) ─────────────────────────────────

@Composable
private fun DeviceCard(stat: DeviceStat, onClick: () -> Unit) {
    val maxPossible = 8 * 60 * 60_000L
    val frac = (stat.totalDuration / maxPossible.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(AccentBlue.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Headset, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stat.deviceName, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("${stat.sessionCount} sessions · ${formatDuration(stat.totalDuration)}",
                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(SpaceSurface2, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(frac).height(4.dp).background(AccentBlue, RoundedCornerShape(2.dp)))
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
    }
}

// ── Session row with long-press delete ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteBox(onDelete: () -> Unit, content: @Composable () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { showDelete = !showDelete }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { content() }
        AnimatedVisibility(visible = showDelete) {
            IconButton(onClick = { onDelete(); showDelete = false }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF4D6A))
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(SpaceSurface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Headset, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(session.deviceName, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(formatDate(session.startTime), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text(formatDuration(session.duration), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, color = AccentBlue)
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
