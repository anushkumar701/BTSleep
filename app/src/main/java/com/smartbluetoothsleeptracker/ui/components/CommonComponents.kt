package com.smartbluetoothsleeptracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.ui.theme.*

// ── Pulsing Glow Dot ─────────────────────────────────────────────────────────
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot_alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

// ── Connection Status Badge ───────────────────────────────────────────────────
@Composable
fun ConnectionBadge(isConnected: Boolean) {
    val color = if (isConnected) ConnectedGreen else TextSecondary
    val label = if (isConnected) "CONNECTED" else "NO DEVICE"
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingDot(color = color, size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

// ── Glass Card ───────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SpaceSurface2.copy(alpha = 0.9f), SpaceSurface.copy(alpha = 0.7f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp)),
        content = content
    )
}

// ── Section Title ─────────────────────────────────────────────────────────────
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        letterSpacing = 1.5.sp,
        modifier = modifier
    )
}

// ── Glow Box ─────────────────────────────────────────────────────────────────
@Composable
fun GlowBox(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.25f), Color.Transparent)
                ),
                shape = CircleShape
            )
        // Note: no .blur() — radial gradient already creates soft glow,
        // blur() requires expensive RenderEffect on older devices
    )
}
