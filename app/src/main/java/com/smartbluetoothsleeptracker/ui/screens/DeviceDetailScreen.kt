package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.DeviceStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    stat: DeviceStat,
    onResetTiming: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onBack: () -> Unit
) {
    var showResetDialog  by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor   = SpaceSurface,
            title = { Text("Reset Timing?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("This clears all recorded usage hours for \"${stat.deviceName}\". Sessions will still appear in history.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onResetTiming(stat.deviceName); showResetDialog = false; onBack() }) {
                    Text("Reset", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            containerColor   = SpaceSurface,
            title = { Text("Remove Device?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("All session history for \"${stat.deviceName}\" will be permanently deleted.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onRemoveDevice(stat.deviceName); showRemoveDialog = false; onBack() }) {
                    Text("Remove", color = Color(0xFFFF4D6A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Details", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceSurface)
            )
        },
        containerColor = DeepSpace
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // Device header card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceSurface, RoundedCornerShape(24.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(72.dp)
                            .background(AccentBlue.copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Headset, null, tint = AccentBlue, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stat.deviceName, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = TextPrimary,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("${stat.sessionCount} sessions recorded",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatPill("Total Time",  formatDur(stat.totalDuration))
                        StatPill("Sessions",    "${stat.sessionCount}")
                    }
                }
            }

            // Action buttons
            item {
                Text("ACTIONS", style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
            }

            item {
                // Reset timing button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceSurface, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).background(Color(0xFFFFB300).copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Refresh, null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Timing", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Clear all recorded usage hours for this device",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary)
                    }
                }
            }

            item {
                // Remove device button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpaceSurface, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).background(Color(0xFFFF4D6A).copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF4D6A), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remove Device", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, color = Color(0xFFFF4D6A))
                        Text("Permanently delete all history for this device",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { showRemoveDialog = true }) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = AccentBlue)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

private fun formatDur(millis: Long): String {
    if (millis <= 0L) return "0m"
    val m = millis / 60_000L; val h = m / 60
    return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
}
