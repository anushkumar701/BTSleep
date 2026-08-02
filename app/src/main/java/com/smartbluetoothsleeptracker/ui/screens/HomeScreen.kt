package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.core.bluetooth.ConnectedDevice
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.HomeViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DIAL_START = 135f
private const val DIAL_SWEEP = 270f
private const val MIN_MIN = 1L
private const val MAX_MIN = 120L

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = state.settings.hapticFeedbackEnabled

    // Helper to conditionally perform haptic feedback
    fun doHaptic(type: HapticFeedbackType = HapticFeedbackType.LongPress) {
        if (hapticEnabled) haptic.performHapticFeedback(type)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = 16.dp,
                start = 24.dp,
                end = 24.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Connection Status Bar ──────────────────────────────────────
        ConnectionStatusBar(
            devices = state.connectedDevices,
            btEnabled = state.btEnabled,
            cooldownActive = state.cooldown.active
        )

        Spacer(Modifier.height(16.dp))

        // ── Cooldown Banner ────────────────────────────────────────────
        if (state.cooldown.active) {
            CooldownBanner(
                expiresAt = state.cooldown.expiresAt,
                onAllowReconnect = { viewModel.allowReconnect() }
            )
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.weight(1f))

        if (state.isTimerRunning) {
            val totalDurationMs = (state.settings.timerPlannedMinutes + state.settings.timerExtendedMinutes) * 60_000L
            CountdownDisplay(remainingMs = state.remainingMs, totalDurationMs = totalDurationMs)
        } else {
            RotaryDial(
                minutes = state.selectedMinutes,
                onMinutesChange = { viewModel.setMinutes(it) },
                haptic = haptic,
                hapticEnabled = hapticEnabled
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Action Buttons ─────────────────────────────────────────────
        if (state.isTimerRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        doHaptic()
                        viewModel.cancelTimer()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        doHaptic()
                        viewModel.extendTimer()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Extend", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = {
                    doHaptic()
                    viewModel.startTimer()
                },
                enabled = state.connectedDevices.isNotEmpty() && state.btEnabled,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    disabledContainerColor = Surface3
                )
            ) {
                Icon(Icons.Rounded.NightsStay, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Start Sleep Timer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            if (state.connectedDevices.isEmpty() || !state.btEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (!state.btEnabled) "Bluetooth is off"
                    else "No audio device connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Connection Status Bar ──────────────────────────────────────────────

@Composable
private fun ConnectionStatusBar(
    devices: List<ConnectedDevice>,
    btEnabled: Boolean,
    cooldownActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface2, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        val dotColor = when {
            cooldownActive -> StatusOrange
            devices.isNotEmpty() -> StatusGreen
            !btEnabled -> StatusRed
            else -> TextTertiary
        }
        Box(Modifier.size(10.dp).background(dotColor, CircleShape))

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            when {
                cooldownActive -> {
                    Text("Cooldown Active", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = StatusOrange)
                }
                devices.isNotEmpty() -> {
                    val primary = devices.firstOrNull { it.isFavorite } ?: devices.first()
                    Text(
                        primary.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (devices.size > 1) {
                        Text(
                            "+${devices.size - 1} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                !btEnabled -> {
                    Text("Bluetooth Off", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = StatusRed)
                }
                else -> {
                    Text("No device connected", style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary)
                }
            }
        }

        // Favorite indicator for primary device
        if (devices.isNotEmpty()) {
            val primary = devices.firstOrNull { it.isFavorite }
            if (primary != null) {
                Icon(Icons.Rounded.Star, "Favorited", tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Cooldown Banner ────────────────────────────────────────────────────

@Composable
private fun CooldownBanner(expiresAt: Long, onAllowReconnect: () -> Unit) {
    val remaining = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatusOrange.copy(0.1f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Shield, null, tint = StatusOrange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Reconnect blocked", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = StatusOrange)
            Text("${remaining}s remaining", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Box(
            modifier = Modifier
                .background(StatusGreen.copy(0.15f), RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .clickable { onAllowReconnect() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("Allow now", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = StatusGreen)
        }
    }
}

// ── Countdown Display ──────────────────────────────────────────────────

@Composable
private fun CountdownDisplay(remainingMs: Long, totalDurationMs: Long) {
    val totalSec = (remainingMs / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60

    val progressFraction = if (totalDurationMs > 0) {
        (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background & Progress ring
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val stroke = Stroke(width = 12f, cap = StrokeCap.Round)
                    val inset = 12f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    onDrawBehind {
                        // Gray background track
                        drawArc(Surface3, 0f, 360f, false, topLeft, arcSize, style = stroke)
                        // Active colored sweep matching RotaryDial's styling (AccentBlue, AccentCyan, AccentPurple)
                        if (progressFraction > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(AccentBlue, AccentCyan, AccentPurple)),
                                startAngle = -90f, // start from the top
                                sweepAngle = progressFraction * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = stroke
                            )
                        }
                    }
                }
            )

            // Time text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontSize = if (h > 0) 42.sp else 52.sp
                )
                Spacer(Modifier.height(4.dp))
                Text("remaining", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

// ── Rotary Dial ────────────────────────────────────────────────────────

@Composable
private fun RotaryDial(
    minutes: Long,
    onMinutesChange: (Long) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    hapticEnabled: Boolean = true
) {
    val fraction = ((minutes - MIN_MIN).toFloat() / (MAX_MIN - MIN_MIN).toFloat()).coerceIn(0f, 1f)
    val sweepAngle = fraction * DIAL_SWEEP

    var lastSnapped by remember { mutableStateOf(minutes) }
    var centerPx by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(0f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val stroke = Stroke(width = 20f, cap = StrokeCap.Round)
                        val inset = 20f / 2f
                        val arcRect = Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = Offset(inset, inset)
                        centerPx = Offset(size.width / 2f, size.height / 2f)
                        sizePx = size.width / 2f - inset

                        onDrawBehind {
                            // Track
                            drawArc(Surface3, DIAL_START, DIAL_SWEEP, false, topLeft, arcRect, style = stroke)
                            // Fill
                            if (sweepAngle > 0f) {
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(AccentBlue, AccentCyan, AccentPurple)),
                                    startAngle = DIAL_START,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcRect,
                                    style = stroke
                                )
                                // Thumb
                                val thumbRad = Math.toRadians((DIAL_START + sweepAngle).toDouble())
                                val tx = centerPx.x + sizePx * cos(thumbRad).toFloat()
                                val ty = centerPx.y + sizePx * sin(thumbRad).toFloat()
                                drawCircle(Color.White, 14f, Offset(tx, ty))
                                drawCircle(AccentBlue, 8f, Offset(tx, ty))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val dx = change.position.x - centerPx.x
                            val dy = change.position.y - centerPx.y
                            val rawAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
                            val dialAngle = ((rawAngle.toFloat() - DIAL_START + 360f) % 360f).coerceIn(0f, DIAL_SWEEP)
                            val frac = dialAngle / DIAL_SWEEP
                            val snapped = (MIN_MIN + (frac * (MAX_MIN - MIN_MIN)).toLong()).coerceIn(MIN_MIN, MAX_MIN)
                            if (snapped != lastSnapped) {
                                lastSnapped = snapped
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onMinutesChange(snapped)
                        }
                    }
            )

            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val h = minutes / 60L
                val m = minutes % 60L
                val timeStr = if (h > 0) {
                    if (m > 0) "${h}h ${m}m" else "${h}h"
                } else "${m}m"

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text("minutes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(15L, 30L, 45L, 60L, 90L, 120L).forEach { preset ->
                val selected = minutes == preset
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) AccentBlue else Surface2,
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinutesChange(preset)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (preset >= 60) "${preset / 60}h${if (preset % 60 > 0) "${preset % 60}" else ""}" else "${preset}m",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) TextOnAccent else TextSecondary
                    )
                }
            }
        }
    }
}
