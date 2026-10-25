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
    totalSteps: Int = 6,
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
                totalSteps = 6,
                badge = "STEP 1 OF 6 • SLEEP TIMER",
                title = "Set Duration with Rotary Dial",
                prompt = "👉 Rotate the smooth dial or tap any quick preset (15m, 30m, 45m, 60m) below to choose when audio stops.",
                completedPrompt = "✓ Great job! Set to ${selectedMinutes}m. You can set anywhere from 1 to 120 minutes.",
                icon = Icons.Rounded.Schedule,
                accentColor = AccentBlue
            ),
            InteractiveStage(
                step = 2,
                totalSteps = 6,
                badge = "STEP 2 OF 6 • VOLUME FADE",
                title = "Gentle Sleep Fade Out",
                prompt = "🎵 Instead of cutting audio abruptly (which jolts your brain awake), volume glides down smoothly over 10–30s.\n\n💡 Pro tip: If you're still awake while it fades, tap any physical volume button on your phone to add +5 mins!",
                completedPrompt = "✓ Volume fade configured! Tap the 'Volume Fade' card below anytime to customize duration.",
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                accentColor = AccentPurple
            ),
            InteractiveStage(
                step = 3,
                totalSteps = 6,
                badge = "STEP 3 OF 6 • RECONNECT BLOCKER",
                title = "Sleep Protection Cooldown",
                prompt = "💤 When Bluetooth turns off, some earbuds keep trying to reconnect, beeping in your ear and draining battery.\n\nSleepBT blocks reconnects during your cooldown (0–120s) so you stay asleep peacefully.",
                completedPrompt = "✓ Reconnect Blocker active! Earbuds are prevented from waking you up.",
                icon = Icons.Rounded.Shield,
                accentColor = AccentTeal
            ),
            InteractiveStage(
                step = 4,
                totalSteps = 6,
                badge = "STEP 4 OF 6 • USAGE & TARGETS",
                title = "Usage Stats & Primary Target",
                prompt = "📊 Check the Usage tab to see daily & weekly listening history.\n\n⭐ Tap the Star ★ on your connected earbuds on Home or Usage to mark it as your Primary Sleep Target!",
                completedPrompt = "✓ Usage tracking active! Sessions and favorite targets are securely stored on device.",
                icon = Icons.Rounded.BarChart,
                accentColor = AccentCyan
            ),
            InteractiveStage(
                step = 5,
                totalSteps = 6,
                badge = "STEP 5 OF 6 • HEARING HEALTH",
                title = "WHO-Standard Ear Care",
                prompt = "❤️ The Health tab monitors your daily audio dose, ear rest score, and fatigue risk (Low, Moderate, High) to protect your hearing over time.",
                completedPrompt = "✓ Ear health monitored! Keeps your ears safe and well-rested.",
                icon = Icons.Rounded.Favorite,
                accentColor = StatusGreen
            ),
            InteractiveStage(
                step = 6,
                totalSteps = 6,
                badge = "STEP 6 OF 6 • SETTINGS & CONTROLS",
                title = "Tailor Your Preferences",
                prompt = "⚙️ In Settings, adjust auto-extend minutes, warning alert lead times, dark/light theme, and optional rootless Shizuku controls.\n\nYou're all set!",
                completedPrompt = "✓ You are ready! Tap 'Start Sleep Timer' below whenever you head to bed.",
                icon = Icons.Rounded.Settings,
                accentColor = StatusOrange
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
