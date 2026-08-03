package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.view.HapticFeedbackConstants
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticEnabled = state.settings.hapticFeedbackEnabled

    LaunchedEffect(Unit) {
        com.smartbluetoothsleeptracker.core.firebase.FirebaseManager.logHomeScreenLoad(context)
    }

    // Helper to conditionally perform haptic feedback
    fun doHaptic(type: Int = HapticFeedbackConstants.LONG_PRESS) {
        if (hapticEnabled) {
            view.isHapticFeedbackEnabled = true
            view.performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                top = 8.dp,
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Connection Status Bar ──────────────────────────────────────
        ConnectionStatusBar(
            devices = state.connectedDevices,
            btEnabled = state.btEnabled,
            cooldownActive = state.cooldown.active,
            isDisconnectReady = state.isDisconnectReady
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
                onMinutesChange = { viewModel.setMinutesEphemeral(it) },
                onMinutesChangeFinished = { viewModel.saveMinutes(it) },
                view = view,
                hapticEnabled = hapticEnabled,
                lastUsedPreset = state.lastUsedPreset,
                onPresetSelected = { preset ->
                    viewModel.saveLastUsedPreset(preset)
                }
            )
        }

        // ── Smart Duration Suggestion ──────────────────────────────────
        if (!state.isTimerRunning && state.suggestedMinutes != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(AccentPurple.copy(0.1f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.applySuggestion(state.suggestedMinutes!!) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        tint = AccentPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.suggestionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Action Buttons ─────────────────────────────────────────────
        if (state.isTimerRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        doHaptic()
                        viewModel.cancelTimer()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        doHaptic()
                        if (state.isPaused) viewModel.resumeTimer() else viewModel.pauseTimer()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isPaused) StatusOrange else Surface3)
                ) {
                    Icon(
                        if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        null,
                        Modifier.size(18.dp),
                        tint = if (state.isPaused) Color.White else TextPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (state.isPaused) "Resume" else "Pause",
                        fontWeight = FontWeight.Bold,
                        color = if (state.isPaused) Color.White else TextPrimary,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {
                        doHaptic()
                        viewModel.extendTimer()
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Extend", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        } else {
            // ── Breathing Start Button ─────────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "breathe")
            val breatheScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.035f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breatheScale"
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.0f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowAlpha"
            )

            val isEnabled = state.connectedDevices.isNotEmpty() && state.btEnabled

            Box(modifier = Modifier.fillMaxWidth()) {
                // Glow ring behind button
                if (isEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 4.dp)
                            .background(
                                AccentBlue.copy(alpha = glowAlpha),
                                RoundedCornerShape(20.dp)
                            )
                    )
                }

                Button(
                    onClick = {
                        doHaptic()
                        viewModel.startTimer()
                    },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .then(if (isEnabled) Modifier.scale(breatheScale) else Modifier),
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
    cooldownActive: Boolean,
    isDisconnectReady: Boolean
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            primary.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isDisconnectReady) "Ready" else "Unverified",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDisconnectReady) StatusGreen else StatusOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    (if (isDisconnectReady) StatusGreen else StatusOrange).copy(0.12f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
    val remainingSeconds by produceState(
        initialValue = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000,
        key1 = expiresAt
    ) {
        while (System.currentTimeMillis() < expiresAt) {
            value = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
            kotlinx.coroutines.delay(200)
        }
        value = 0L
    }

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
            Text("${remainingSeconds}s remaining", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Background & Progress ring
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val strokeWidth = 16.dp.toPx()
                    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    val inset = strokeWidth / 2f
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

// ── Rotary Dial with Fine/Coarse Adjustment ────────────────────────────

@Composable
private fun RotaryDial(
    minutes: Long,
    onMinutesChange: (Long) -> Unit,
    onMinutesChangeFinished: (Long) -> Unit,
    view: android.view.View,
    hapticEnabled: Boolean = true,
    lastUsedPreset: Long = 0L,
    onPresetSelected: (Long) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fraction = ((minutes - MIN_MIN).toFloat() / (MAX_MIN - MIN_MIN).toFloat()).coerceIn(0f, 1f)
    val sweepAngle = fraction * DIAL_SWEEP

    var lastSnapped by remember { mutableStateOf(minutes) }
    var centerPx by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(0f) }
    var isFineMode by remember { mutableStateOf(false) }
    var showModeIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(minutes) {
        lastSnapped = minutes
    }

    // Auto-hide mode indicator after drag ends
    LaunchedEffect(showModeIndicator) {
        if (showModeIndicator) {
            kotlinx.coroutines.delay(1500)
            showModeIndicator = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val strokeWidth = 24.dp.toPx()
                        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        val inset = strokeWidth / 2f
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
                                drawCircle(Color.White, 16.dp.toPx(), Offset(tx, ty))
                                drawCircle(AccentBlue, 10.dp.toPx(), Offset(tx, ty))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var fineDetected = false
                            var dragStarted = false
                            val downTime = System.currentTimeMillis()
                            val longPressThreshold = 300L

                            // Wait for either movement or long press
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    // Finger lifted before drag
                                    if (dragStarted) {
                                        onMinutesChangeFinished(lastSnapped)
                                        showModeIndicator = false
                                    }
                                    break
                                }

                                val elapsed = System.currentTimeMillis() - downTime
                                val moved = (change.position - down.position).getDistance() > 20f

                                if (!dragStarted && moved) {
                                    dragStarted = true
                                    fineDetected = elapsed >= longPressThreshold
                                    isFineMode = fineDetected
                                    showModeIndicator = true
                                } else if (!dragStarted && elapsed >= longPressThreshold && !moved) {
                                    // Long press detected but no movement yet — haptic feedback
                                    if (hapticEnabled) {
                                        view.isHapticFeedbackEnabled = true
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                                    }
                                    fineDetected = true
                                    isFineMode = true
                                    showModeIndicator = true
                                }

                                if (dragStarted) {
                                    change.consume()
                                    val dx = change.position.x - centerPx.x
                                    val dy = change.position.y - centerPx.y
                                    val rawAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0

                                    val rawSnapped = if (rawAngle > 45.0 && rawAngle < 135.0) {
                                        if (rawAngle > 90.0) MIN_MIN else MAX_MIN
                                    } else {
                                        val dialAngle = ((rawAngle.toFloat() - DIAL_START + 360f) % 360f).coerceIn(0f, DIAL_SWEEP)
                                        val frac = dialAngle / DIAL_SWEEP
                                        (MIN_MIN + (frac * (MAX_MIN - MIN_MIN)).toLong()).coerceIn(MIN_MIN, MAX_MIN)
                                    }

                                    // Minute-by-minute step increments (1-min steps by default)
                                    val snapped = rawSnapped.coerceIn(MIN_MIN, MAX_MIN)

                                    val isJump = kotlin.math.abs(snapped - lastSnapped) > (MAX_MIN - MIN_MIN) / 2
                                    val finalSnapped = if (isJump) {
                                        if (lastSnapped < (MAX_MIN - MIN_MIN) / 2) MIN_MIN else MAX_MIN
                                    } else {
                                        snapped
                                    }
                                    if (finalSnapped != lastSnapped) {
                                        lastSnapped = finalSnapped
                                        if (hapticEnabled) {
                                            view.isHapticFeedbackEnabled = true
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.KEYBOARD_TAP,
                                                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                            )
                                            com.smartbluetoothsleeptracker.core.haptics.HapticManager.vibrateTick(context)
                                        }
                                    }
                                    onMinutesChange(finalSnapped)
                                }
                            }
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

                // Fine/Coarse mode indicator
                if (showModeIndicator) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isFineMode) "Fine · 1m steps" else "Coarse · 5m steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFineMode) AccentCyan else AccentPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick presets with "last used" highlight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(15L, 30L, 45L, 60L, 90L, 120L).forEach { preset ->
                val selected = minutes == preset
                val isLastUsed = lastUsedPreset == preset && !selected
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) AccentBlue else Surface2,
                            RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (isLastUsed) Modifier.border(
                                1.5.dp, AccentBlue.copy(0.6f), RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (hapticEnabled) {
                                view.isHapticFeedbackEnabled = true
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                            }
                            onMinutesChange(preset)
                            onMinutesChangeFinished(preset)
                            onPresetSelected(preset)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (preset >= 60) "${preset / 60}h${if (preset % 60 > 0) "${preset % 60}" else ""}" else "${preset}m",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected || isLastUsed) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            selected -> TextOnAccent
                            isLastUsed -> AccentBlue
                            else -> TextSecondary
                        }
                    )
                }
            }
        }
    }
}
