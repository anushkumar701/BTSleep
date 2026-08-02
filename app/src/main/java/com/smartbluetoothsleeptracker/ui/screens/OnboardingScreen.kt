package com.smartbluetoothsleeptracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.R
import com.smartbluetoothsleeptracker.ui.theme.*

enum class OnboardingStep { TOS, NOTIFICATION, BLUETOOTH, BATTERY, DONE }

@Composable
fun OnboardingScreen(
    initialStep: OnboardingStep = OnboardingStep.TOS,
    onTosAccepted: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(initialStep) }
    var tosChecked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> step = OnboardingStep.BLUETOOTH }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> step = OnboardingStep.BATTERY }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D111A), Color(0xFF07090F), Color(0xFF040508))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "onboarding"
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.TOS -> TosStep(
                    checked = tosChecked,
                    onCheckedChange = { tosChecked = it },
                    onContinue = {
                        onTosAccepted()
                        step = OnboardingStep.NOTIFICATION
                    }
                )
                OnboardingStep.NOTIFICATION -> PermissionStep(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    description = "Get alerts when your timer is about to end, with options to extend or disconnect immediately.",
                    onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onSkip = { step = OnboardingStep.BLUETOOTH }
                )
                OnboardingStep.BLUETOOTH -> PermissionStep(
                    icon = Icons.Rounded.Bluetooth,
                    title = "Bluetooth Access",
                    description = "Required to detect connected audio devices and disconnect them when the timer expires.",
                    onRequest = {
                        btLauncher.launch(arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        ))
                    },
                    onSkip = { step = OnboardingStep.BATTERY }
                )
                OnboardingStep.BATTERY -> BatteryStep(
                    onRequest = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        step = OnboardingStep.DONE
                    },
                    onSkip = { step = OnboardingStep.DONE }
                )
                OnboardingStep.DONE -> {
                    LaunchedEffect(Unit) { onComplete() }
                }
            }
        }
    }
}

@Composable
private fun TosStep(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val termsContent = remember { loadLocalTermsAsset(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // App Logo centered near top
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentBlue.copy(0.35f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "SleepBT Logo",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Small App Name & Subtitle
        Text(
            "SleepBT",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 0.5.sp
        )
        Text(
            "Terms of Service & Privacy Policy",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))

        // Scrollable Terms & Privacy Content Box with Fade Edge Indicators
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(BorderStroke(1.dp, SurfaceBorder), RoundedCornerShape(16.dp))
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                RenderTermsMarkdown(termsContent)
            }

            // Top Fade Edge Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Surface1, Color.Transparent)
                        )
                    )
            )

            // Bottom Fade Edge Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Surface1)
                        )
                    )
            )
        }

        Spacer(Modifier.height(16.dp))

        // Agreement Checkbox Card
        Surface(
            color = if (checked) AccentBlue.copy(0.1f) else Surface1,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onCheckedChange(!checked) },
            border = BorderStroke(
                1.dp,
                if (checked) AccentBlue else SurfaceBorder
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentBlue,
                        uncheckedColor = TextTertiary,
                        checkmarkColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "I have read and agree to the Terms of Service and Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                    color = if (checked) TextPrimary else Color(0xFFCCCCCC),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Primary Continue Button
        Button(
            onClick = onContinue,
            enabled = checked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = Surface3,
                contentColor = Color.White,
                disabledContentColor = TextTertiary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (checked) 6.dp else 0.dp
            )
        ) {
            Text(
                "Agree & Continue",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RenderTermsMarkdown(content: String) {
    val lines = content.split("\n")
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("---") -> {
                    HorizontalDivider(
                        color = SurfaceBorder,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("• ", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = trimmed.removePrefix("* ").trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFE6E6E6),
                                lineHeight = 20.sp,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
                trimmed.isNotEmpty() -> {
                    Text(
                        text = trimmed,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFE6E6E6),
                            lineHeight = 20.sp,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

private fun loadLocalTermsAsset(context: Context): String {
    return try {
        context.assets.open("terms_and_privacy.md").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "SleepBT Terms of Service & Privacy Policy\n\n100% On-Device & Offline Operation. No analytics or server tracking."
    }
}

@Composable
private fun PermissionStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onRequest: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(AccentBlue.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) {
            Text("Grant Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = TextTertiary)
        }
    }
}

@Composable
private fun BatteryStep(onRequest: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(StatusOrange.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.BatteryAlert, null, tint = StatusOrange, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Battery Optimization", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Some phone manufacturers aggressively kill background apps. " +
            "Exempting SleepBT from battery optimization ensures your sleep timer " +
            "keeps running even when your screen is off.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusOrange)
        ) {
            Text("Exempt from Battery Saver", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DeepBlack)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = TextTertiary)
        }
    }
}
