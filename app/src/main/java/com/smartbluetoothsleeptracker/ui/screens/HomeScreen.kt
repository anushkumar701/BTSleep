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
private const val DIAL_MIN_MIN     = 1L     // minimum selectable minutes
private const val DIAL_MAX_MIN     = 60L    // maximum selectable minutes (1 hour) — spec: max 60

/** Maps a raw angle (0–360) onto the dial arc [0, DIAL_SWEEP] */
private fun angleToDial(angleDeg: Float): Float {
    // Offset so 0° = start of arc
    var a = (angleDeg - DIAL_START_ANGLE + 360f) % 360f
    // Wrap: anything beyond DIAL_SWEEP+tolerance → clamp
    return a.coerceIn(0f, DIAL_SWEEP)
}

/** Maps dial fraction [0,1] → minutes [1, 60] snapped to 5-min steps */
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

    // Subtle glow pulse when running
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f, targetValue = 0.16f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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

        // Background glow blob when running
        if (state.timerRunning) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)
                    .background(
                        Brush.radialGradient(listOf(AccentBlue.copy(alpha = glowAlpha), Color.Transparent)),
                        CircleShape
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Status Card ──────────────────────────────────────────────────
            StatusCard(
                deviceName    = state.deviceName,
                isConnected   = state.isConnected,
                blockerActive = state.blockerActive,
                clockText     = clockText
            )

            // ── Warning Banner ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = !state.bluetoothEnabled || (!state.isConnected && !isActive),
                enter = fadeIn(tween(300)) + expandVertically(),
                exit  = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Spacer(Modifier.height(10.dp))
                WarningBanner(btOff = !state.bluetoothEnabled)
            }

            Spacer(Modifier.weight(1f))

            // ── Main Area: Dial (idle) OR Timer Ring (active) ────────────────
            if (!isActive) {
                // Draggable circular dial for setting time
                DialTimerSelector(
                    minutes   = state.selectedMinutes,
                    onMinutes = { homeVm.setMinutes(it) },
                    haptic    = haptic
                )
    } else {
        // Active countdown ring — spec: animated countdown ring
        TimerRing(
            progress    = arcProgress,
            isPaused    = state.timerPaused,
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

            // ── Control Buttons ──────────────────────────────────────────────
            ControlButtons(
                isRunning = state.timerRunning,
                isPaused  = state.timerPaused,
                btReady   = state.isConnected && state.bluetoothEnabled,
                onStart   = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartTimer(state.selectedMinutes)
                },
                onCancel  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCancelTimer()
                },
                onExtend  = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExtendTimer()
                }
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Dial Timer Selector ────────────────────────────────────────────────────────

/**
 * A draggable circular dial: user drags finger around the circle to set sleep time.
 * Arc spans 270° (bottom-left → bottom-right). Snaps to 5-min increments.
 */
@Composable
private fun DialTimerSelector(
    minutes: Long,
    onMinutes: (Long) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    // Convert current minutes → dial fraction for drawing
    val fraction = ((minutes - DIAL_MIN_MIN).toFloat() / (DIAL_MAX_MIN - DIAL_MIN_MIN).toFloat()).coerceIn(0f, 1f)
    val sweepAngle = fraction * DIAL_SWEEP

    var lastSnapped by remember { mutableStateOf(minutes) }
    var centerPx by remember { mutableStateOf(Offset.Zero) }
    var sizePx   by remember { mutableStateOf(0f) }

    val dialSize = 280.dp

    Box(
        modifier = Modifier.size(dialSize),
        contentAlignment = Alignment.Center
    ) {
        // Canvas for track + filled arc + thumb dot
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
                    val inset  = 22f / 2f
                    val arcRect = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    centerPx = Offset(size.width / 2f, size.height / 2f)
                    sizePx   = size.width / 2f - inset

                    onDrawBehind {
                        // Track (grey)
                        drawArc(
                            color = SpaceSurface2,
                            startAngle = DIAL_START_ANGLE,
                            sweepAngle = DIAL_SWEEP,
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcRect,
                            style      = stroke
                        )
                        // Filled arc (blue gradient)
                        if (sweepAngle > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(AccentBlue.copy(0.7f), AccentBlue, AccentCyan.copy(0.8f))
                                ),
                                startAngle = DIAL_START_ANGLE,
                                sweepAngle = sweepAngle,
                                useCenter  = false,
                                topLeft    = topLeft,
                                size       = arcRect,
                                style      = stroke
                            )
                            // Thumb dot at tip of arc
                            val thumbAngleRad = Math.toRadians((DIAL_START_ANGLE + sweepAngle).toDouble())
                            val r = sizePx
                            val thumbX = centerPx.x + r * cos(thumbAngleRad).toFloat()
                            val thumbY = centerPx.y + r * sin(thumbAngleRad).toFloat()
                            drawCircle(color = Color.White, radius = 14f, center = Offset(thumbX, thumbY))
                            drawCircle(color = AccentBlue,  radius = 9f,  center = Offset(thumbX, thumbY))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            centerPx = Offset(size.width / 2f, size.height / 2f)
                            sizePx   = size.width / 2f - 11f
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

        // Center text display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val h = minutes / 60L
            val m = minutes % 60L
            val timeStr = if (h > 0) {
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else "${m}m"

            Text(
                text = timeStr,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "drag to set",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            // Quick-select chips below center — spec: [10m, 20m, 30m, 60m]
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10L, 20L, 30L, 60L).forEach { preset ->
                    val sel = minutes == preset
                    Box(
                        modifier = Modifier
                            .background(if (sel) AccentBlue else SpaceSurface2, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMinutes(preset)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (preset >= 60) "${preset / 60}h" else "${preset}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) Color.White else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ── Active Timer Ring (shown when timer is running/paused) ─────────────────────
@Composable
private fun TimerRing(progress: Float, isPaused: Boolean, countdownText: String) {
    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        // Track
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(260.dp),
            strokeWidth = 10.dp,
            color = SpaceSurface2,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        // Filled arc
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(260.dp),
            strokeWidth = 10.dp,
            color = if (isPaused) AccentPurple else AccentBlue,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )
        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isPaused) {
                Text("PAUSED", style = MaterialTheme.typography.labelMedium,
                    color = AccentPurple, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = countdownText,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = if (isPaused) 46.sp else 52.sp),
                fontWeight = FontWeight.Bold,
                color = if (isPaused) AccentPurple else TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text("UNTIL DISCONNECT", style = MaterialTheme.typography.labelSmall,
                color = TextSecondary, letterSpacing = 1.5.sp)
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(deviceName: String?, isConnected: Boolean, blockerActive: Boolean, clockText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpaceSurface, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            ConnectionBadge(isConnected = isConnected)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    blockerActive -> "Blocking reconnect..."
                    isConnected   -> deviceName ?: "Bluetooth Device"
                    else          -> "No device connected"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(clockText, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.size(28.dp)
                    .background(
                        when {
                            blockerActive -> ErrorRed.copy(0.15f)
                            isConnected   -> ConnectedGreen.copy(0.12f)
                            else          -> SpaceSurface2
                        }, CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (blockerActive) Icons.Rounded.Block else Icons.Rounded.Bluetooth,
                    null,
                    tint = when {
                        blockerActive -> ErrorRed
                        isConnected   -> ConnectedGreen
                        else          -> TextTertiary
                    },
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(btOff: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (btOff) ErrorRed.copy(0.10f) else Warning.copy(0.10f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Warning, null,
            tint = if (btOff) ErrorRed else Warning, modifier = Modifier.size(16.dp))
        Text(
            text = if (btOff) "Bluetooth is off — turn it on to use the timer"
                   else        "Connect a Bluetooth device to start the sleep timer",
            style = MaterialTheme.typography.bodySmall,
            color = if (btOff) ErrorRed else Warning,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PausedBanner() {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(AccentPurple.copy(0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PulsingDot(color = AccentPurple)
        Text(
            "Timer paused — will resume when device reconnects",
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
    btReady: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onExtend: () -> Unit
) {
    AnimatedContent(
        targetState = isRunning || isPaused,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "btn_anim"
    ) { active ->
        if (active) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceSurface2)
                    ) {
                        Icon(Icons.Rounded.Stop, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold, color = ErrorRed, style = MaterialTheme.typography.labelLarge)
                    }
                    if (isRunning) {
                        Button(
                            onClick = onExtend,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+10 MIN", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStart,
                    enabled = btReady,
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        disabledContainerColor = SpaceSurface2
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, null,
                        tint = if (btReady) Color.White else TextTertiary,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START TIMER", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (btReady) Color.White else TextTertiary)
                }
                if (!btReady) {
                    Spacer(Modifier.height(6.dp))
                    Text("Requires a connected Bluetooth device",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
