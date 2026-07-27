package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.ui.components.ConnectionBadge
import com.smartbluetoothsleeptracker.ui.components.PulsingDot
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ── Constants ──────────────────────────────────────────────────────────────────
private const val DIAL_START_ANGLE = 135f   // degrees — bottom-left
private const val DIAL_SWEEP       = 270f   // degrees swept for full range
private const val DIAL_MIN_MIN     = 5L     // minimum selectable minutes
private const val DIAL_MAX_MIN     = 120L   // maximum selectable minutes (2 hours)

private fun angleToDial(angleDeg: Float): Float {
    var a = (angleDeg - DIAL_START_ANGLE + 360f) % 360f
    return a.coerceIn(0f, DIAL_SWEEP)
}

private fun fractionToMinutes(fraction: Float): Long {
    val raw = (fraction * (DIAL_MAX_MIN - DIAL_MIN_MIN) + DIAL_MIN_MIN).toLong()
    return ((raw + 2L) / 5L * 5L).coerceIn(DIAL_MIN_MIN, DIAL_MAX_MIN)
}

@Composable
fun HomeScreen(
    homeVm: HomeViewModel,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onExtendTimer: () -> Unit,
    onDisconnectNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state    by homeVm.state.collectAsStateWithLifecycle()
    val haptic   = LocalHapticFeedback.current
    val isActive = state.timerRunning || state.timerPaused

    // Progress arc (animated)
    val arcProgress by animateFloatAsState(
        targetValue = if (isActive && state.totalTimerMillis > 0L)
            (state.remainingMillis.toFloat() / state.totalTimerMillis).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600, easing = LinearEasing),
        label = "arc_progress"
    )

    // Breathing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    // Live clock
    var clockText by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            clockText = currentTime()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DeepSpace)) {

        // Ambient Sleep Pulse Background Halo
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .offset(y = (-30).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isActive) AccentPurple.copy(alpha = glowAlpha) else AccentBlue.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Status Header Card ──────────────────────────────────────────
            StatusCard(
                deviceName    = state.deviceName,
                isConnected   = state.isConnected,
                btEnabled     = state.bluetoothEnabled,
                blockerActive = state.blockerActive,
                clockText     = clockText,
                onDisconnectNow = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDisconnectNow()
                }
            )

            // ── Warning Banner (Informational only, never blocks user!) ───
            AnimatedVisibility(
                visible = !state.bluetoothEnabled,
                enter = fadeIn(tween(300)) + expandVertically(),
                exit  = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Spacer(Modifier.height(10.dp))
                WarningBanner(btOff = true)
            }

            Spacer(Modifier.weight(1f))

            // ── Main Dial OR Countdown Ring ────────────────────────────────
            if (!isActive) {
                DialTimerSelector(
                    minutes   = state.selectedMinutes,
                    onMinutes = { homeVm.setMinutes(it) },
                    haptic    = haptic
                )
            } else {
                TimerRing(
                    progress      = arcProgress,
                    isPaused      = state.timerPaused,
                    countdownText = state.countdownText
                )
            }

            // Paused banner
            AnimatedVisibility(visible = state.timerPaused) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    PausedBanner()
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Main Action Controls ───────────────────────────────────────
            ControlButtons(
                isRunning       = state.timerRunning,
                isPaused        = state.timerPaused,
                isConnected     = state.isConnected,
                selectedMinutes = state.selectedMinutes,
                onStart         = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartTimer(state.selectedMinutes)
                },
                onCancel        = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancelTimer()
                },
                onExtend        = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExtendTimer()
                },
                onDisconnectNow = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDisconnectNow()
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Dial Timer Selector ────────────────────────────────────────────────────────
@Composable
private fun DialTimerSelector(
    minutes: Long,
    onMinutes: (Long) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val fraction = ((minutes - DIAL_MIN_MIN).toFloat() / (DIAL_MAX_MIN - DIAL_MIN_MIN).toFloat()).coerceIn(0f, 1f)
    val sweepAngle = fraction * DIAL_SWEEP

    var lastSnapped by remember { mutableStateOf(minutes) }
    var centerPx by remember { mutableStateOf(Offset.Zero) }
    var sizePx   by remember { mutableStateOf(0f) }

    val dialSize = 290.dp

    Box(
        modifier = Modifier.size(dialSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val stroke = Stroke(width = 24f, cap = StrokeCap.Round)
                    val inset  = 24f / 2f
                    val arcRect = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    centerPx = Offset(size.width / 2f, size.height / 2f)
                    sizePx   = size.width / 2f - inset

                    onDrawBehind {
                        // Track
                        drawArc(
                            color = SpaceSurface2,
                            startAngle = DIAL_START_ANGLE,
                            sweepAngle = DIAL_SWEEP,
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcRect,
                            style      = stroke
                        )
                        // Filled Arc (Cyan to Purple Gradient)
                        if (sweepAngle > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(AccentBlue, AccentCyan, AccentPurple)
                                ),
                                startAngle = DIAL_START_ANGLE,
                                sweepAngle = sweepAngle,
                                useCenter  = false,
                                topLeft    = topLeft,
                                size       = arcRect,
                                style      = stroke
                            )
                            // Glowing Thumb Knob
                            val thumbAngleRad = Math.toRadians((DIAL_START_ANGLE + sweepAngle).toDouble())
                            val r = sizePx
                            val thumbX = centerPx.x + r * cos(thumbAngleRad).toFloat()
                            val thumbY = centerPx.y + r * sin(thumbAngleRad).toFloat()
                            drawCircle(color = Color.White, radius = 16f, center = Offset(thumbX, thumbY))
                            drawCircle(color = AccentBlue,  radius = 10f, center = Offset(thumbX, thumbY))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            centerPx = Offset(size.width / 2f, size.height / 2f)
                            sizePx   = size.width / 2f - 12f
                        }
                    ) { change, _ ->
                        change.consume()
                        val dx = change.position.x - centerPx.x
                        val dy = change.position.y - centerPx.y
                        val angleRaw = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
                        val dialAngle = angleToDial(angleRaw.toFloat())
                        val frac      = dialAngle / DIAL_SWEEP
                        val snapped   = fractionToMinutes(frac)
                        if (snapped != lastSnapped) {
                            lastSnapped = snapped
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onMinutes(snapped)
                    }
                }
        )

        // Center Time Text Display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val h = minutes / 60L
            val m = minutes % 60L
            val timeStr = if (h > 0) {
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else "${m}m"

            Text(
                text = timeStr,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "SLEEP TIMER DURATION",
                style = MaterialTheme.typography.labelSmall,
                color = AccentBlue,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(14.dp))

            // Quick Preset Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15L, 30L, 45L, 60L, 90L).forEach { preset ->
                    val selected = minutes == preset
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) AccentBlue else SpaceSurface2,
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMinutes(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (preset >= 60) "${preset / 60}h" else "${preset}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextOnAccent else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ── Countdown Ring ─────────────────────────────────────────────────────────────
@Composable
private fun TimerRing(progress: Float, isPaused: Boolean, countdownText: String) {
    Box(modifier = Modifier.size(290.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(270.dp),
            strokeWidth = 12.dp,
            color = SpaceSurface2,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(270.dp),
            strokeWidth = 12.dp,
            color = if (isPaused) AccentPurple else AccentBlue,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isPaused) {
                Text(
                    "PAUSED",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentPurple,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = countdownText,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = if (isPaused) 46.sp else 54.sp),
                fontWeight = FontWeight.Black,
                color = if (isPaused) AccentPurple else TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "UNTIL AUTO DISCONNECT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
        }
    }
}

// ── Status Header Card ─────────────────────────────────────────────────────────
@Composable
private fun StatusCard(
    deviceName: String?,
    isConnected: Boolean,
    btEnabled: Boolean,
    blockerActive: Boolean,
    clockText: String,
    onDisconnectNow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionBadge(isConnected = isConnected)
                if (blockerActive) {
                    Text(
                        "BLOCKING",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    blockerActive -> "Blocking auto-reconnect"
                    isConnected   -> deviceName ?: "Bluetooth Device Connected"
                    btEnabled     -> "Bluetooth Active (Standby)"
                    else          -> "Bluetooth Radio Off"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Quick Emergency Disconnect Button right in header
            if (isConnected || blockerActive) {
                Box(
                    modifier = Modifier
                        .background(ErrorRed.copy(0.15f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDisconnectNow() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "OFF NOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed,
                        fontSize = 10.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = clockText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(btOff: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warning.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Warning, null, tint = Warning, modifier = Modifier.size(18.dp))
        Text(
            text = "Bluetooth radio is currently off. Timer will turn off Bluetooth hardware when countdown ends.",
            style = MaterialTheme.typography.bodySmall,
            color = Warning,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PausedBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentPurple.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PulsingDot(color = AccentPurple)
        Text(
            "Timer paused — will resume automatically when audio device reconnects",
            style = MaterialTheme.typography.bodySmall,
            color = AccentPurple,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ControlButtons(
    isRunning: Boolean,
    isPaused: Boolean,
    isConnected: Boolean,
    selectedMinutes: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onExtend: () -> Unit,
    onDisconnectNow: () -> Unit
) {
    AnimatedContent(
        targetState = isRunning || isPaused,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "btn_anim"
    ) { active ->
        if (active) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // STOP TIMER BUTTON
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceSurface2)
                    ) {
                        Icon(Icons.Rounded.Stop, null, tint = ErrorRed, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold, color = ErrorRed, style = MaterialTheme.typography.titleMedium)
                    }

                    // EXTEND TIMER BUTTON
                    Button(
                        onClick = onExtend,
                        modifier = Modifier.weight(1f).height(60.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = TextOnAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+10 MIN", fontWeight = FontWeight.Bold, color = TextOnAccent, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // INSTANT HARD DISCONNECT BUTTON
                Button(
                    onClick = onDisconnectNow,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(0.15f))
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DISCONNECT BLUETOOTH NOW", fontWeight = FontWeight.Bold, color = ErrorRed, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // START TIMER BUTTON (ALWAYS ENABLED!)
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = TextOnAccent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "START SLEEP TIMER ($selectedMinutes MINS)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = TextOnAccent
                    )
                }

                // DISCONNECT NOW QUICK TEST BUTTON
                OutlinedButton(
                    onClick = onDisconnectNow,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Icon(Icons.Rounded.BluetoothDisabled, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "DISCONNECT BLUETOOTH NOW (MANUAL TEST)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
