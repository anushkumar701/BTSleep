package com.smartbluetoothsleeptracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val hapticEnabled = state.settings.hapticFeedbackEnabled


    // Helper to conditionally perform haptic feedback (reserved exclusively for rotary dialer)
    fun doHaptic(@Suppress("UNUSED_PARAMETER") type: Int = HapticFeedbackConstants.LONG_PRESS) {
        // No-op: Vibration is restricted exclusively to the rotary dialer per user request
    }

    var showCooldownPopup by rememberSaveable { mutableStateOf(true) }
    var showFadeDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockDialog by rememberSaveable { mutableStateOf(false) }

    val isTutorialActive = !state.settings.tutorialCompleted && !state.isTimerRunning && !state.cooldown.active
    var tutorialStep by rememberSaveable { mutableIntStateOf(1) }
    var userInteractedWithDial by rememberSaveable { mutableStateOf(false) }
    var userInteractedWithFade by rememberSaveable { mutableStateOf(false) }
    var initialMinutes by rememberSaveable { mutableLongStateOf(-1L) }
    val scrollState = rememberScrollState()

    LaunchedEffect(tutorialStep, isTutorialActive) {
        if (isTutorialActive) {
            when (tutorialStep) {
                1 -> scrollState.animateScrollTo(0)
                2 -> scrollState.animateScrollTo(500)
                3 -> scrollState.animateScrollTo(350)
                4 -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialMinutes == -1L) {
            initialMinutes = state.selectedMinutes
        }
    }

    LaunchedEffect(state.selectedMinutes) {
        if (initialMinutes != -1L && state.selectedMinutes != initialMinutes) {
            userInteractedWithDial = true
        }
    }

    LaunchedEffect(showFadeDialog) {
        if (showFadeDialog) {
            userInteractedWithFade = true
        }
    }

    LaunchedEffect(state.cooldown.expiresAt) {
        if (state.cooldown.active && state.cooldown.expiresAt > System.currentTimeMillis()) {
            showCooldownPopup = true
        } else if (!state.cooldown.active) {
            showCooldownPopup = false
        }
    }

    if (showCooldownPopup && state.cooldown.active && state.cooldown.expiresAt > System.currentTimeMillis()) {
        CooldownPopUpDialog(
            expiresAt = state.cooldown.expiresAt,
            onAllowReconnect = {
                showCooldownPopup = false
                viewModel.allowReconnect()
            },
            onDismiss = {
                showCooldownPopup = false
            }
        )
    }

    if (showFadeDialog) {
        AlertDialog(
            onDismissRequest = { showFadeDialog = false },
            containerColor = Surface2,
            title = {
                Text("Volume Fade Duration", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Audio volume will smoothly ramp down to zero before disconnecting.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    val fadeOptions = listOf(5, 10, 30, 60, 120, 300)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fadeOptions.take(3).forEach { sec ->
                            val selected = state.settings.fadeOutDurationSeconds == sec
                            Button(
                                onClick = {
                                    doHaptic()
                                    viewModel.setFadeOutDurationSeconds(sec)
                                    showFadeDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) AccentBlue else Surface3,
                                    contentColor = if (selected) Color.White else TextPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(if (sec >= 60) "${sec / 60}m" else "${sec}s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fadeOptions.drop(3).forEach { sec ->
                            val selected = state.settings.fadeOutDurationSeconds == sec
                            Button(
                                onClick = {
                                    doHaptic()
                                    viewModel.setFadeOutDurationSeconds(sec)
                                    showFadeDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) AccentBlue else Surface3,
                                    contentColor = if (selected) Color.White else TextPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(if (sec >= 60) "${sec / 60}m" else "${sec}s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFadeDialog = false }) {
                    Text("Close", color = AccentBlue)
                }
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            containerColor = Surface2,
            title = {
                Text("Reconnect Cooldown", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Block Bluetooth devices from reconnecting for this protection window:",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    val blockOptions = listOf(15, 30, 45, 60, 90, 120)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        blockOptions.take(3).forEach { sec ->
                            val selected = state.settings.cooldownSeconds == sec
                            Button(
                                onClick = {
                                    doHaptic()
                                    viewModel.setCooldownSeconds(sec)
                                    showBlockDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) StatusOrange else Surface3,
                                    contentColor = if (selected) Color.White else TextPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("${sec}s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        blockOptions.drop(3).forEach { sec ->
                            val selected = state.settings.cooldownSeconds == sec
                            Button(
                                onClick = {
                                    doHaptic()
                                    viewModel.setCooldownSeconds(sec)
                                    showBlockDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) StatusOrange else Surface3,
                                    contentColor = if (selected) Color.White else TextPrimary
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(if (sec >= 60) "${sec / 60}m" else "${sec}s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Close", color = StatusOrange)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = 8.dp,
                    bottom = if (isTutorialActive) 240.dp else 24.dp,
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
                onToggleFavorite = { viewModel.togglePrimaryDevice(it) }
            )

            Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = Pair(state.cooldown.active, state.isTimerRunning),
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + slideInVertically { it / 8 }).togetherWith(
                    fadeOut(animationSpec = tween(400))
                )
            },
            label = "MainContent",
            modifier = Modifier.fillMaxWidth()
        ) { (cooldownActive, timerRunning) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (cooldownActive) {
            // ── MAIN RECONNECT BLOCKER VIEW ON HOME SCREEN WHEN TIMER ENDS ──
            CooldownMainCard(
                expiresAt = state.cooldown.expiresAt,
                onAllowReconnect = { viewModel.allowReconnect() }
            )
                } else if (timerRunning) {
            // ── ACTIVE TIMER VIEW ──
            val totalDurationMs = (state.settings.timerPlannedMinutes + state.settings.timerExtendedMinutes) * 60_000L
            CountdownDisplay(remainingMs = state.remainingMs, totalDurationMs = totalDurationMs)

            Spacer(Modifier.height(16.dp))

            // Action Buttons (Cancel, Pause, Extend) IMMEDIATELY below timer!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        doHaptic()
                        viewModel.cancelTimer()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusRed,
                        containerColor = StatusRed.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                val pauseBgColor by animateColorAsState(if (state.isPaused) StatusOrange else Surface3, label = "pauseBg")
                val pauseContentColor = if (state.isPaused) Color.White else TextPrimary
                
                Button(
                    onClick = {
                        doHaptic()
                        if (state.isPaused) viewModel.resumeTimer() else viewModel.pauseTimer()
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pauseBgColor),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(targetState = state.isPaused, label = "pauseIcon") { paused ->
                            Icon(
                                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                null,
                                Modifier.size(20.dp),
                                tint = pauseContentColor
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (state.isPaused) "Resume" else "Pause",
                            fontWeight = FontWeight.Bold,
                            color = pauseContentColor,
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        doHaptic()
                        viewModel.extendTimer()
                    },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("+${state.settings.extendMinutes}m", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Active Sleep Protection Details Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Surface2),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusGreen.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulseGreen")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                            label = "scale"
                        )
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.5f,
                            animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                            label = "alpha"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(scale)
                                .graphicsLayer { this.alpha = alpha }
                                .background(StatusGreen, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Sleep Protection Running",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = StatusGreen
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Surface3, thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            val activeDev = state.connectedDevices.firstOrNull()
                            val devIcon = if (activeDev?.type == com.smartbluetoothsleeptracker.data.db.DeviceType.WIRED_HEADPHONES) Icons.Rounded.Headphones else Icons.Rounded.Bluetooth
                            Icon(devIcon, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                activeDev?.name ?: "Connected Device",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(Modifier.width(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (state.settings.playbackStopEnabled) "${state.settings.fadeOutDurationSeconds}s fade" else "No fade",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            doHaptic()
                            viewModel.endNow()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Surface3,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "End Now & Disconnect",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
                } else {
            // ── IDLE DIAL VIEW ──
            RotaryDial(
                minutes = state.selectedMinutes,
                onMinutesChange = { viewModel.setMinutesEphemeral(it) },
                onMinutesChangeFinished = { viewModel.saveMinutes(it) },
                hapticEnabled = hapticEnabled
            )

            // ── Smart Duration Suggestion ──────────────────────────────────
            val suggested = state.suggestedMinutes
            if (suggested != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(AccentPurple.copy(0.1f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.applySuggestion(suggested) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
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

            Spacer(Modifier.height(12.dp))

            // ── Quick Presets & Live 12-Hour End Time Preview ───────────────
            val presets = listOf(15L, 30L, 45L, 60L)

            if (isTutorialActive && tutorialStep == 1) {
                Surface(
                    color = (if (userInteractedWithDial) StatusGreen else AccentBlue).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, (if (userInteractedWithDial) StatusGreen else AccentBlue).copy(alpha = 0.7f)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (userInteractedWithDial) "✓ ${state.selectedMinutes}m selected! Tap 'Next Step' in tutorial above" else "👆 Turn dial or tap any preset pill (15m, 30m, 45m, 60m)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (userInteractedWithDial) StatusGreen else AccentBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTutorialActive && tutorialStep == 1) {
                            Modifier
                                .border(
                                    width = 2.dp,
                                    brush = Brush.horizontalGradient(listOf(AccentBlue, AccentCyan, AccentPurple)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(4.dp)
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { presetMin ->
                    val isSelected = state.selectedMinutes == presetMin
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(
                                if (isSelected) AccentBlue.copy(alpha = 0.2f) else Surface2,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentBlue else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setMinutesEphemeral(presetMin)
                                viewModel.saveMinutes(presetMin)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${presetMin}m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AccentBlue else TextSecondary
                        )
                    }
                }
            }

            var currentClockTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(10_000L)
                    currentClockTime = System.currentTimeMillis()
                }
            }
            val endTimeMs = currentClockTime + state.selectedMinutes * 60_000L
            val endTimeFormatted = remember(endTimeMs / 60_000L) {
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(endTimeMs))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Timer ends at $endTimeFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Live Connected Device & Battery Status ───────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTutorialActive && tutorialStep == 3) {
                            Modifier
                                .border(
                                    width = 2.dp,
                                    brush = Brush.horizontalGradient(listOf(StatusGreen, AccentCyan)),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(2.dp)
                        } else Modifier
                    )
            ) {
                DeviceBatteryCard(devices = state.connectedDevices)
            }

            Spacer(Modifier.height(14.dp))

            // ── Interactive Sleep Protection Options Row ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Volume Fade
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    border = if (isTutorialActive && tutorialStep == 2) {
                        BorderStroke(
                            2.dp,
                            Brush.horizontalGradient(listOf(AccentPurple, AccentCyan))
                        )
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (state.settings.playbackStopEnabled) {
                                showFadeDialog = true
                            } else {
                                doHaptic()
                                viewModel.setPlaybackStop(true)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isTutorialActive && tutorialStep == 2) {
                            Text(
                                text = if (userInteractedWithFade) "✓ Configured!" else "👆 Tap to customize",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (userInteractedWithFade) StatusGreen else AccentPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (state.settings.playbackStopEnabled) AccentBlue.copy(0.15f) else Surface3,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeDown,
                                    null,
                                    tint = if (state.settings.playbackStopEnabled) AccentBlue else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Switch(
                                checked = state.settings.playbackStopEnabled,
                                onCheckedChange = {
                                    doHaptic()
                                    viewModel.setPlaybackStop(it)
                                },
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentBlue
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Volume Fade",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (state.settings.playbackStopEnabled) "${state.settings.fadeOutDurationSeconds}s (tap to set)" else "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.settings.playbackStopEnabled) AccentBlue else TextTertiary
                        )
                    }
                }

                // Option 2: Reconnect Block
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (state.settings.reconnectBlockerEnabled) {
                                showBlockDialog = true
                            } else {
                                doHaptic()
                                viewModel.setReconnectBlocker(true)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (state.settings.reconnectBlockerEnabled) StatusOrange.copy(0.15f) else Surface3,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Shield,
                                    null,
                                    tint = if (state.settings.reconnectBlockerEnabled) StatusOrange else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Switch(
                                checked = state.settings.reconnectBlockerEnabled,
                                onCheckedChange = {
                                    doHaptic()
                                    viewModel.setReconnectBlocker(it)
                                },
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = StatusOrange
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Reconnect Block",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (state.settings.reconnectBlockerEnabled) "${state.settings.cooldownSeconds}s (tap to set)" else "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.settings.reconnectBlockerEnabled) StatusOrange else TextTertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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

            val isEnabled = state.connectedDevices.isNotEmpty() && (state.btEnabled || state.connectedDevices.any { it.type == com.smartbluetoothsleeptracker.data.db.DeviceType.WIRED_HEADPHONES })

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

            if (isTutorialActive && tutorialStep == 4) {
                Surface(
                    color = StatusGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "🚀 Tap 'Start Sleep Timer' to begin protection!",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTutorialActive && tutorialStep == 4) {
                            Modifier
                                .border(
                                    width = 2.5.dp,
                                    brush = Brush.horizontalGradient(listOf(StatusGreen, AccentCyan, AccentBlue)),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(2.dp)
                        } else Modifier
                    )
            ) {
                Button(
                    onClick = {
                        doHaptic()
                        if (isTutorialActive) {
                            viewModel.setTutorialCompleted(true)
                        }
                        viewModel.startTimer()
                    },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .graphicsLayer {
                            scaleX = if (isEnabled) breatheScale else 1f
                            scaleY = if (isEnabled) breatheScale else 1f
                        },
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
            }

            if (!isEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (!state.btEnabled && state.connectedDevices.isEmpty()) "Bluetooth is off & no wired headset"
                    else "No audio device connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Floating Docked Interactive In-App Tutorial Guided Tour ──
    AnimatedVisibility(
        visible = isTutorialActive,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 16.dp,
            tonalElevation = 8.dp,
            color = Color.Transparent
        ) {
            com.smartbluetoothsleeptracker.ui.components.InteractiveTutorialBanner(
                step = tutorialStep,
                totalSteps = 6,
                userInteractedWithDial = userInteractedWithDial,
                userInteractedWithFade = userInteractedWithFade,
                selectedMinutes = state.selectedMinutes,
                onStepChange = { tutorialStep = it },
                onComplete = {
                    viewModel.setTutorialCompleted(true)
                },
                onSkip = {
                    viewModel.setTutorialCompleted(true)
                }
            )
        }
    }
}
}

// ── Connection Status Bar ──────────────────────────────────────────────

@Composable
private fun ConnectionStatusBar(
    devices: List<ConnectedDevice>,
    btEnabled: Boolean,
    cooldownActive: Boolean,
    onToggleFavorite: (String) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isClickableAction = (devices.isEmpty() || !btEnabled) && !cooldownActive
    val rowModifier = Modifier
        .fillMaxWidth()
        .background(Surface2, RoundedCornerShape(16.dp))
        .then(
            if (isClickableAction) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
            } else Modifier
        )
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        val dotColor = when {
            cooldownActive -> StatusOrange
            devices.isNotEmpty() -> StatusGreen
            !btEnabled -> StatusRed
            else -> TextTertiary
        }
        val animatedDotColor by animateColorAsState(dotColor, label = "dotColor")

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val isPulsing = devices.isNotEmpty() && !cooldownActive
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isPulsing) 1.3f else 1f,
            animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
            label = "pulseScale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isPulsing) 0.5f else 1f,
            animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
            label = "pulseAlpha"
        )

        Box(Modifier.size(10.dp).scale(scale).graphicsLayer { this.alpha = alpha }.background(animatedDotColor, CircleShape))

        Spacer(Modifier.width(12.dp))

        AnimatedContent(
            targetState = Triple(cooldownActive, devices, btEnabled),
            label = "StatusText",
            modifier = Modifier.weight(1f)
        ) { target ->
            val cdActive = target.first
            val devs = target.second
            val btOn = target.third
            Column {
                when {
                    cdActive -> {
                        Text("Cooldown Active", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = StatusOrange)
                    }
                    devs.isNotEmpty() -> {
                        val primary = devs.firstOrNull { it.isFavorite } ?: devs.first()
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
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        StatusGreen.copy(0.12f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    if (devs.size > 1) {
                        Text(
                            "+${devs.size - 1} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                !btOn -> {
                    Text("Bluetooth Off", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = StatusRed)
                    Text("Tap to turn on Bluetooth", style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary)
                }
                else -> {
                    Text("No device connected", style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary)
                    Text("Tap to connect earbuds", style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary)
                }
            }
        }
    }

        // Action or Favorite indicator
        if (devices.isNotEmpty()) {
            val primary = devices.firstOrNull { it.isFavorite }
            val target = primary ?: devices.first()
            IconButton(
                onClick = { onToggleFavorite(target.address) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (primary != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (primary != null) "Primary Sleep Target" else "Set as Primary Sleep Target",
                    tint = if (primary != null) AccentBlue else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (isClickableAction) {
            Icon(Icons.Rounded.ChevronRight, "Open Bluetooth settings", tint = TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Cooldown Banner & Pop-up ───────────────────────────────────────────

@Composable
private fun CooldownBanner(
    expiresAt: Long,
    onAllowReconnect: () -> Unit,
    onOpenPopup: () -> Unit
) {
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
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenPopup() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Shield, null, tint = StatusOrange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Reconnect blocked", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = StatusOrange)
            Text("${remainingSeconds}s remaining — tap for details", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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

@Composable
private fun CooldownMainCard(
    expiresAt: Long,
    onAllowReconnect: () -> Unit
) {
    val initialSec = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
    val remainingSeconds by produceState(
        initialValue = initialSec,
        key1 = expiresAt
    ) {
        while (System.currentTimeMillis() < expiresAt) {
            value = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
            kotlinx.coroutines.delay(200)
        }
        value = 0L
    }

    val maxCooldownSec = 60f
    val progress = (remainingSeconds.toFloat() / maxCooldownSec).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(1.dp, StatusOrange.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(StatusOrange.copy(0.15f), CircleShape)
                    .border(2.dp, StatusOrange.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = StatusOrange,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Reconnect Protection Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "SleepBT is actively preventing auto-reconnection to ensure undisturbed sleep.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = StatusOrange,
                    trackColor = StatusOrange.copy(alpha = 0.15f),
                    strokeWidth = 6.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${remainingSeconds}s",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOrange
                    )
                    Text(
                        text = "remaining",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onAllowReconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Allow Reconnect Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun CooldownPopUpDialog(
    expiresAt: Long,
    onAllowReconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialSec = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
    val remainingSeconds by produceState(
        initialValue = initialSec,
        key1 = expiresAt
    ) {
        while (System.currentTimeMillis() < expiresAt) {
            value = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
            kotlinx.coroutines.delay(200)
        }
        value = 0L
        onDismiss()
    }

    val maxCooldownSec = 60f
    val progress = (remainingSeconds.toFloat() / maxCooldownSec).coerceIn(0f, 1f)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1),
            border = BorderStroke(1.dp, StatusOrange.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Shield Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(StatusOrange.copy(0.15f), CircleShape)
                        .border(2.dp, StatusOrange.copy(0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = StatusOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Reconnect Protection Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "SleepBT is actively preventing auto-reconnection to ensure undisturbed sleep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Running timer ring inside pop-up
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = StatusOrange,
                        trackColor = StatusOrange.copy(alpha = 0.15f),
                        strokeWidth = 6.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${remainingSeconds}s",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusOrange
                        )
                        Text(
                            text = "remaining",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = onAllowReconnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow Reconnect Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Blocked", color = TextSecondary, fontSize = 13.sp)
                }
            }
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
    
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    
    val isFinalMinute = remainingMs <= 60_000L
    val gradientColors = if (isFinalMinute) listOf(StatusOrange, StatusRed) else listOf(AccentBlue, AccentCyan, AccentPurple)

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
                    val blurStroke = Stroke(width = strokeWidth * 1.8f, cap = StrokeCap.Round)
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    onDrawBehind {
                        // Gray background track
                        drawArc(Surface3, 0f, 360f, false, topLeft, arcSize, style = stroke)
                        // Active colored sweep
                        if (animatedProgress > 0f) {
                            val sweepBrush = Brush.sweepGradient(gradientColors)
                            // Outer glow
                            drawArc(
                                brush = sweepBrush,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = blurStroke,
                                alpha = 0.25f
                            )
                            // Main arc
                            drawArc(
                                brush = sweepBrush,
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 360f,
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
                    text = if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.ROOT, "%d:%02d", m, s),
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontSize = 48.sp
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
    hapticEnabled: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fraction = ((minutes - MIN_MIN).toFloat() / (MAX_MIN - MIN_MIN).toFloat()).coerceIn(0f, 1f)
    val sweepAngle = fraction * DIAL_SWEEP

    var lastSnapped by remember { mutableLongStateOf(minutes) }
    var centerPx by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(minutes) {
        lastSnapped = minutes
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
                            var dragStarted = false
                            val downTime = System.currentTimeMillis()
                            val longPressThreshold = 300L

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!change.pressed) {
                                    if (dragStarted) {
                                        onMinutesChangeFinished(lastSnapped)
                                    }
                                    break
                                }

                                val elapsed = System.currentTimeMillis() - downTime
                                val moved = (change.position - down.position).getDistance() > 20f

                                if (!dragStarted && moved) {
                                    dragStarted = true
                                } else if (!dragStarted && elapsed >= longPressThreshold && !moved) {
                                    if (hapticEnabled) {
                                        com.smartbluetoothsleeptracker.core.haptics.HapticManager.vibrateTick(context)
                                    }
                                    dragStarted = true
                                }

                                if (dragStarted) {
                                    change.consume()
                                    val dx = change.position.x - centerPx.x
                                    val dy = change.position.y - centerPx.y
                                    val rawAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0

                                    val relativeAngle = (rawAngle.toFloat() - DIAL_START + 360f) % 360f
                                    val snapped = if (relativeAngle > DIAL_SWEEP) {
                                        // In bottom dead gap (270°..360°) — clamp to nearest edge without jumping
                                        if (relativeAngle >= 315f || lastSnapped <= 30L) {
                                            MIN_MIN
                                        } else {
                                            MAX_MIN
                                        }
                                    } else {
                                        val dialAngle = relativeAngle.coerceIn(0f, DIAL_SWEEP)
                                        val frac = dialAngle / DIAL_SWEEP
                                        val calculated = (MIN_MIN + (frac * (MAX_MIN - MIN_MIN)).toLong()).coerceIn(MIN_MIN, MAX_MIN)
                                        // Prevent accidental wrap jumps during rapid gestures near edges
                                        if (lastSnapped <= 10L && calculated >= 100L) {
                                            MIN_MIN
                                        } else if (lastSnapped >= 110L && calculated <= 20L) {
                                            MAX_MIN
                                        } else {
                                            calculated
                                        }
                                    }

                                    if (snapped != lastSnapped) {
                                        lastSnapped = snapped
                                        if (hapticEnabled) {
                                            com.smartbluetoothsleeptracker.core.haptics.HapticManager.vibrateTick(context)
                                        }
                                    }
                                    onMinutesChange(snapped)
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
            }
        }
    }
}

@Composable
private fun DeviceBatteryCard(devices: List<ConnectedDevice>) {
    val activeDevice = devices.firstOrNull { it.isFavorite } ?: devices.firstOrNull()

    if (activeDevice == null) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Connect earbuds or neckband to view live battery",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        return
    }

    val battery = activeDevice.battery ?: com.smartbluetoothsleeptracker.core.bluetooth.BatteryInfo()
    val isEarbuds = activeDevice.type == com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS
    val context = LocalContext.current
    val app = context.applicationContext as? com.smartbluetoothsleeptracker.SleepBTApp

    val hasScanPermission = remember(activeDevice.address) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    // Poll case battery while Home screen is visible and an earbuds device is connected
    LaunchedEffect(activeDevice.address, hasScanPermission) {
        val dev = activeDevice.device ?: return@LaunchedEffect
        com.smartbluetoothsleeptracker.core.bluetooth.FastPairNotificationListener.instance?.get()?.syncActiveNotifications()
        if (isEarbuds) {
            while (isActive) {
                app?.btMonitor?.queryCaseBattery(dev, activeDevice.name)
                com.smartbluetoothsleeptracker.core.bluetooth.FastPairNotificationListener.instance?.get()?.syncActiveNotifications()
                delay(60_000L)
            }
        }
    }

    if (isEarbuds) {
        // ── Earbuds: Left, Right, Case ──────────────────────────────
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(AccentPurple.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = AccentPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = activeDevice.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Surface3, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TWS Earbuds",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val caseBat = battery.caseBattery
                val caseLvl = if (battery.caseLevel in 0..100) battery.caseLevel else -1
                val isCaseKnown = caseLvl in 0..100

                // Left, Right, Case battery cells
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftLvl = when {
                        battery.leftLevel in 0..100 -> battery.leftLevel
                        battery.mainLevel in 0..100 -> battery.mainLevel
                        battery.rightLevel in 0..100 -> battery.rightLevel
                        else -> -1
                    }
                    val rightLvl = when {
                        battery.rightLevel in 0..100 -> battery.rightLevel
                        battery.mainLevel in 0..100 -> battery.mainLevel
                        battery.leftLevel in 0..100 -> battery.leftLevel
                        else -> -1
                    }
                    val now = System.currentTimeMillis()
                    val isStale = isCaseKnown && caseBat != null && (now - caseBat.seenAtMillis > 5 * 60 * 1000L)
                    val caseSubtext = when {
                        isStale && caseBat != null -> {
                            val mins = ((now - caseBat.seenAtMillis) / 60_000L).coerceAtLeast(1L)
                            "seen ${mins}m ago"
                        }
                        else -> null
                    }

                    EarbudBatteryItem(
                        label = "Left",
                        level = leftLvl,
                        icon = Icons.Rounded.Hearing
                    )

                    VerticalDivider(
                        modifier = Modifier.height(26.dp),
                        color = Surface3,
                        thickness = 1.dp
                    )

                    EarbudBatteryItem(
                        label = "Right",
                        level = rightLvl,
                        icon = Icons.Rounded.Hearing
                    )

                    if (isCaseKnown) {
                        VerticalDivider(
                            modifier = Modifier.height(26.dp),
                            color = Surface3,
                            thickness = 1.dp
                        )

                        EarbudBatteryItem(
                            label = "Case",
                            level = caseLvl,
                            icon = Icons.Rounded.Inbox,
                            isCharging = caseBat?.charging,
                            subtext = caseSubtext
                        )
                    }
                }

                if (!isCaseKnown && !com.smartbluetoothsleeptracker.core.bluetooth.FastPairNotificationListener.isAccessGranted(context)) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface3.copy(alpha = 0.5f))
                            .clickable {
                                com.smartbluetoothsleeptracker.core.bluetooth.FastPairNotificationListener.openNotificationAccessSettings(context)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Tap to enable Fast Pair sync for Case battery",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = AccentBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    } else {
        // ── Single Device Battery Pill (Neckband, Headphones, or Unified TWS) ──
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(AccentBlue.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = activeDevice.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val typeLabel = when (activeDevice.type) {
                            com.smartbluetoothsleeptracker.data.db.DeviceType.NECKBAND -> "Neckband"
                            com.smartbluetoothsleeptracker.data.db.DeviceType.EARBUDS -> "TWS Earbuds"
                            com.smartbluetoothsleeptracker.data.db.DeviceType.WIRED_HEADPHONES -> "Wired Headphones"
                            else -> "Connected Audio"
                        }
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Battery Indicator Pill
                val mainLvl = battery.mainLevel
                val batteryColor = when {
                    mainLvl >= 50 -> StatusGreen
                    mainLvl in 20..49 -> StatusOrange
                    mainLvl in 0..19 -> StatusRed
                    else -> TextTertiary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(batteryColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            mainLvl >= 80 -> Icons.Rounded.BatteryFull
                            mainLvl >= 50 -> Icons.Rounded.Battery5Bar
                            mainLvl >= 20 -> Icons.Rounded.Battery3Bar
                            mainLvl in 0..19 -> Icons.Rounded.BatteryAlert
                            else -> Icons.Rounded.Battery0Bar
                        },
                        contentDescription = null,
                        tint = batteryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (mainLvl in 0..100) "$mainLvl%" else "Connected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = batteryColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EarbudBatteryItem(
    label: String,
    level: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCharging: Boolean? = null,
    subtext: String? = null
) {
    val isKnown = level in 0..100
    val color = when {
        !isKnown -> TextTertiary
        level >= 50 -> StatusGreen
        level in 20..49 -> StatusOrange
        level in 0..19 -> StatusRed
        else -> TextTertiary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = TextTertiary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isKnown) "$level%" else "--",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (isCharging == true) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Charging",
                        tint = StatusGreen,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (!subtext.isNullOrBlank()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextTertiary,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
