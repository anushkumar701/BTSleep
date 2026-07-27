package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradient: List<Color>
)

private val PAGES = listOf(
    OnboardingPage(
        icon = Icons.Rounded.Bedtime,
        title = "Sleep Smarter",
        subtitle = "SleepBT automatically disconnects your Bluetooth earbuds at your set bedtime — so you wake up refreshed, not tangled.",
        gradient = listOf(DeepSpace, SpaceSurface)
    ),
    OnboardingPage(
        icon = Icons.Rounded.Bluetooth,
        title = "Effortless Control",
        subtitle = "Set a sleep timer, pick your device, and relax. SleepBT works silently in the background — even when your phone screen is off.",
        gradient = listOf(DeepSpace, SpaceSurfaceLowest)
    ),
    OnboardingPage(
        icon = Icons.Rounded.HealthAndSafety,
        title = "Protect Your Hearing",
        subtitle = "Track your daily listening hours and get ear health insights based on WHO guidelines. Your ears will thank you.",
        gradient = listOf(DeepSpace, SpaceSurface2)
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState { PAGES.size + 1 } // +1 for T&C page
    val scope = rememberCoroutineScope()
    var termsAgreed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page < PAGES.size) {
                // Feature pages
                val p = PAGES[page]
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(p.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon ring
                        Box(
                            modifier = Modifier.size(120.dp)
                                .background(AccentBlue.copy(0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(88.dp)
                                    .background(AccentBlue.copy(0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(p.icon, null, tint = AccentBlue, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            p.subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp
                        )
                    }
                }
            } else {
                // Terms & Conditions page
                TermsPage(
                    agreed = termsAgreed,
                    onAgreedChange = { termsAgreed = it },
                    onGetStarted = onComplete
                )
            }
        }

        // Bottom controls overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PAGES.size + 1) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) AccentBlue
                                else SpaceSurface2
                            )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // Next / Skip
            if (pagerState.currentPage < PAGES.size) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(PAGES.size) }
                    }) {
                        Text("Skip", color = TextSecondary)
                    }
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsPage(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SpaceSurfaceLowest, DeepSpace)))
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        Icon(Icons.Rounded.Shield, null, tint = AccentBlue, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Privacy & Terms",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Please review our terms before getting started",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        // Terms card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceSurface, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            TermsItem(Icons.Rounded.Bluetooth, "Bluetooth Access",
                "We use Bluetooth permissions solely to monitor and disconnect audio devices for your sleep timer.")
            Spacer(Modifier.height(14.dp))
            TermsItem(Icons.Rounded.Storage, "Local Data Only",
                "All data (timer settings, session history) is stored locally on your device. We never collect or transmit personal data.")
            Spacer(Modifier.height(14.dp))
            TermsItem(Icons.Rounded.Notifications, "Notifications",
                "We send notifications only for active sleep timers and 2-minute disconnect warnings.")
            Spacer(Modifier.height(14.dp))
            TermsItem(Icons.Rounded.HealthAndSafety, "Ear Health",
                "Health scores are estimates based on usage time only. This is not medical advice. Consult a doctor for hearing concerns.")
        }

        Spacer(Modifier.weight(1f))

        // Agreement checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = onAgreedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentBlue,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = Color.White
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "I agree to the Terms of Service and Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))

        // Get Started
        Button(
            onClick = onGetStarted,
            enabled = agreed,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = SpaceSurface2
            )
        ) {
            Icon(Icons.Rounded.RocketLaunch, null,
                tint = if (agreed) Color.White else TextTertiary,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Get Started",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (agreed) Color.White else TextTertiary
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TermsItem(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).background(AccentBlue.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}
