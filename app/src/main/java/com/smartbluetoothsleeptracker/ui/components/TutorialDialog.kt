package com.smartbluetoothsleeptracker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.ui.theme.*

data class InteractiveStage(
    val step: Int,
    val totalSteps: Int,
    val badge: String,
    val title: String,
    val prompt: String,
    val completedPrompt: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Hands-on Interactive In-App Tutorial Banner.
 * Positioned directly on the Home Screen so the user can ACTUALLY touch, turn, and click
 * the live rotary dialer, preset pills, volume fade card, and start timer button.
 */
@Composable
fun InteractiveTutorialBanner(
    step: Int,
    totalSteps: Int = 4,
    userInteractedWithDial: Boolean,
    userInteractedWithFade: Boolean,
    selectedMinutes: Long,
    onStepChange: (Int) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stages = remember {
        listOf(
            InteractiveStage(
                step = 1,
                totalSteps = 4,
                badge = "STEP 1 • SET SLEEP TIMER",
                title = "Set Your Duration",
                prompt = "👉 Try it now: Drag the dial or tap any preset pill (15m, 30m, 45m, 60m) below!",
                completedPrompt = "✓ Great job! You set the timer to ${selectedMinutes}m. Turn the dial anytime from 1 to 120m.",
                icon = Icons.Rounded.Schedule,
                accentColor = AccentBlue
            ),
            InteractiveStage(
                step = 2,
                totalSteps = 4,
                badge = "STEP 2 • SLEEP PROTECTION",
                title = "Volume Fade & Reconnect Blocker",
                prompt = "🔊 Volume Fade: Gently lowers music over 10–30s before stopping so sudden silence doesn't wake you up.\n\n🛡️ Reconnect Blocker: Prevents your earbuds or speaker from waking back up and reconnecting while you are sleeping.\n\n👉 Tap 'Volume Fade' or 'Reconnect Cooldown' below to customize!",
                completedPrompt = "✓ Sleep protection configured! Music fades out gently, and Bluetooth reconnects are blocked while you sleep.",
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                accentColor = AccentPurple
            ),
            InteractiveStage(
                step = 3,
                totalSteps = 4,
                badge = "STEP 3 • BATTERY MONITOR",
                title = "Live Headphone Telemetry",
                prompt = "👉 Monitors your Left bud, Right bud, and charging case battery in real time so you wake up powered.",
                completedPrompt = "✓ Earbud telemetry monitored! SleepBT also tracks your daily and weekly hearing exposure.",
                icon = Icons.Rounded.BatteryChargingFull,
                accentColor = StatusGreen
            ),
            InteractiveStage(
                step = 4,
                totalSteps = 4,
                badge = "STEP 4 • READY FOR SLEEP",
                title = "Start Sleep Protection",
                prompt = "👉 When you lie down for bed, tap the Start button below. SleepBT handles the rest automatically!",
                completedPrompt = "✓ You are ready! Music will stop, Bluetooth will disconnect, and your battery will be saved.",
                icon = Icons.Rounded.NightsStay,
                accentColor = StatusGreen
            )
        )
    }

    val currentStage = stages.getOrElse(step - 1) { stages.first() }
    val isLast = step >= totalSteps

    // Subtle pulsing animation for active progress element
    val infiniteTransition = rememberInfiniteTransition(label = "bannerGlow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(1.5.dp, currentStage.accentColor.copy(alpha = pulseAlpha)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Top Progress Dots & Skip Button ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress segments
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..totalSteps) {
                        val isCurrent = i == step
                        val isPast = i < step
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(if (isCurrent) 22.dp else 10.dp)
                                .background(
                                    when {
                                        isCurrent -> currentStage.accentColor
                                        isPast -> currentStage.accentColor.copy(alpha = 0.5f)
                                        else -> Surface3
                                    },
                                    CircleShape
                                )
                        )
                    }
                }

                // Skip button
                TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Skip Tutorial ×",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Step Badge & Title ───────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(currentStage.accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentStage.icon,
                        contentDescription = null,
                        tint = currentStage.accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column {
                    Text(
                        text = currentStage.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = currentStage.accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = currentStage.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Live Interactive Instruction Prompt ──────────────────────────
            val isInteracted = when (step) {
                1 -> userInteractedWithDial
                2 -> userInteractedWithFade
                else -> false
            }

            val promptText = if (isInteracted) {
                when (step) {
                    1 -> "✓ Great job! Set to ${selectedMinutes}m. The 12-hour preview below shows your exact end time."
                    2 -> currentStage.completedPrompt
                    else -> currentStage.completedPrompt
                }
            } else {
                currentStage.prompt
            }

            Surface(
                color = if (isInteracted) StatusGreen.copy(alpha = 0.12f) else Surface2,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isInteracted) StatusGreen.copy(alpha = 0.5f) else SurfaceBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInteracted) StatusGreen else TextSecondary,
                    fontWeight = if (isInteracted) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Navigation Buttons ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { onStepChange(step - 1) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, SurfaceBorder),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (isLast) onComplete() else onStepChange(step + 1)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentStage.accentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isLast) "Finish Tutorial 🚀" else "Next Step",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    if (!isLast) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility wrapper. If called as a dialog, dispatches dismiss.
 */
@Composable
fun TutorialDialog(
    onDismiss: () -> Unit
) {
    // Directly dismiss as the interactive in-app tutorial is now hosted natively on HomeScreen
    LaunchedEffect(Unit) {
        onDismiss()
    }
}
