package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.ui.theme.*
import com.smartbluetoothsleeptracker.viewmodel.HealthRisk
import com.smartbluetoothsleeptracker.viewmodel.HealthViewModel

@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val riskColor = when (state.risk) {
        HealthRisk.LOW -> StatusGreen
        HealthRisk.MODERATE -> StatusOrange
        HealthRisk.HIGH -> StatusRed
    }
    val riskLabel = when (state.risk) {
        HealthRisk.LOW -> "Low Risk"
        HealthRisk.MODERATE -> "Moderate"
        HealthRisk.HIGH -> "High Risk"
    }
    val riskAdvice = when (state.risk) {
        HealthRisk.LOW -> "Your listening duration is within safe limits."
        HealthRisk.MODERATE -> "Consider taking regular breaks between listening sessions."
        HealthRisk.HIGH -> "Prolonged listening can impact hearing. Reduce your session duration."
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = 24.dp,
            start = 24.dp, end = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Column {
                Text("Ear Health", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black, color = TextPrimary)
                Text("Earbuds & neckband usage analysis",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        // Risk gauge
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Arc gauge
                val fraction = (state.weekAvgMinutes / 180f).coerceIn(0f, 1f)
                val animFrac by animateFloatAsState(fraction, tween(1000), label = "gauge")

                Box(Modifier.size(180.dp), Alignment.Center) {
                    Box(Modifier.fillMaxSize().drawWithCache {
                        val stroke = Stroke(16f, cap = StrokeCap.Round)
                        val inset = 16f
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        val tl = Offset(inset, inset)
                        onDrawBehind {
                            drawArc(Surface3, 135f, 270f, false, tl, arcSize, style = stroke)
                            drawArc(
                                brush = Brush.sweepGradient(listOf(StatusGreen, StatusOrange, StatusRed)),
                                startAngle = 135f,
                                sweepAngle = 270f * animFrac,
                                useCenter = false,
                                topLeft = tl, size = arcSize, style = stroke
                            )
                        }
                    })
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.weekAvgMinutes}", style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("min/day avg", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Risk badge
                Box(
                    Modifier.background(riskColor.copy(0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(riskLabel, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = riskColor)
                }

                Spacer(Modifier.height(12.dp))
                Text(riskAdvice, style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary, textAlign = TextAlign.Center)
            }
        }

        // Today's usage
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).background(AccentBlue.copy(0.1f), CircleShape), Alignment.Center) {
                    Icon(Icons.Rounded.Today, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Earbuds & neckband listening time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Text(
                    "${state.todayMinutes}m",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = when {
                        state.todayMinutes > 120 -> StatusRed
                        state.todayMinutes > 60 -> StatusOrange
                        else -> StatusGreen
                    }
                )
            }
        }

        // Weekly trend chart
        item {
            Column(
                Modifier.fillMaxWidth()
                    .background(Surface1, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("Weekly Trend", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(24.dp))

                val maxMins = state.weeklyData.map { it.second }.maxOrNull()?.coerceAtLeast(60) ?: 60

                Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    // Dotted grid lines
                    Column(
                        modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("${maxMins}m", "${maxMins / 2}m", "0m").forEach { valLabel ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    valLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    fontSize = 8.sp,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.Start
                                )
                                androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                                    drawLine(
                                        color = SurfaceBorder,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 2f,
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                }
                            }
                        }
                    }

                    // Bars
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 40.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        state.weeklyData.forEach { (_, mins) ->
                            val frac = (mins / maxMins.toFloat()).coerceIn(0.02f, 1f)
                            val color = when {
                                mins > 120 -> StatusRed
                                mins > 60 -> StatusOrange
                                else -> AccentBlue
                            }
                            val animFrac by animateFloatAsState(
                                targetValue = frac,
                                animationSpec = tween(800, easing = FastOutSlowInEasing),
                                label = "wBar"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (mins > 0) {
                                    Text(
                                        "${mins}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .width(16.dp)
                                        .height((80 * animFrac).dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                listOf(color, color.copy(0.3f))
                                            ),
                                            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    state.weeklyData.forEach { (label, _) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (label == "Today") AccentBlue else TextTertiary,
                            fontWeight = if (label == "Today") FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(StatusGreen, "< 1h (Safe)")
                    LegendDot(StatusOrange, "1-2h")
                    LegendDot(StatusRed, "> 2h")
                }
            }
        }

        // Disclaimer
        item {
            Surface(
                color = StatusOrange.copy(0.06f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = StatusOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Estimate based on listening duration only. Volume level is not measured; this is not medical advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
