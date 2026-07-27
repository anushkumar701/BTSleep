package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.HealthViewModel
import com.smartbluetoothsleeptracker.viewmodel.UsageStatus

@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val statusColor by animateColorAsState(
        targetValue = when (state.status) {
            UsageStatus.SAFE    -> ConnectedGreen
            UsageStatus.MODERATE -> Warning
            UsageStatus.HIGH    -> ErrorRed
        },
        animationSpec = tween(600),
        label = "status_color"
    )

    val statusLabel = when (state.status) {
        UsageStatus.SAFE    -> "Safe"
        UsageStatus.MODERATE -> "Moderate"
        UsageStatus.HIGH    -> "High"
    }

    val statusDescription = when (state.status) {
        UsageStatus.SAFE    -> "Your listening time today is within safe limits."
        UsageStatus.MODERATE -> "Consider reducing listening time to stay within safe limits."
        UsageStatus.HIGH    -> "Extended use may impact hearing health over time."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Usage Ring ───────────────────────────────────────────────────
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 12.dp,
                    color = SpaceSurface2,
                    trackColor = Color.Transparent,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Filled ring — proportion of 120 min (upper moderate bound)
                CircularProgressIndicator(
                    progress = { (state.todayMinutes / 120f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 12.dp,
                    color = statusColor,
                    trackColor = Color.Transparent,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Center content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.todayMinutes}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 52.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "minutes",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Status Badge ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .background(statusColor.copy(0.12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Description ──────────────────────────────────────────────────
            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(32.dp))

            // ── Quick guideline (always visible) ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceSurface, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "Guidelines",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                GuidelineRow(ConnectedGreen, "≤ 60 min", "Safe — sustainable daily use")
                Spacer(Modifier.height(8.dp))
                GuidelineRow(Warning, "60–120 min", "Moderate — reduce when possible")
                Spacer(Modifier.height(8.dp))
                GuidelineRow(ErrorRed, "> 120 min", "High — take extended breaks")
                Spacer(Modifier.height(12.dp))
                Text(
                    "⚠ This is informational only. SleepBT cannot measure actual sound volume.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun GuidelineRow(color: Color, range: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                range,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
