package com.smartbluetoothsleeptracker.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.ui.theme.*

enum class OnboardingStep { TOS, NOTIFICATION, BLUETOOTH, BATTERY, DONE }

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardingStep.TOS) }
    var tosChecked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> step = OnboardingStep.BLUETOOTH }

    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> step = OnboardingStep.BATTERY }

    Box(
        modifier = Modifier.fillMaxSize().background(DeepBlack),
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
                    onContinue = { step = OnboardingStep.NOTIFICATION }
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.NightsStay,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "BT Curfew",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sleep timer for Bluetooth audio",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        // ToS text
        Surface(
            color = Surface2,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Terms of Use", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This app disconnects Bluetooth devices on a timer. It uses reflection-based APIs " +
                    "and optional Shizuku for privileged control. The health tab provides duration-based " +
                    "estimates only — not medical advice. This app is not distributed on the Play Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentBlue,
                    uncheckedColor = TextTertiary
                )
            )
            Spacer(Modifier.width(8.dp))
            Text("I agree to the terms of use", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            enabled = checked,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = Surface3
            )
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
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
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(AccentBlue.copy(0.12f), CircleShape),
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
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(StatusOrange.copy(0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.BatteryAlert, null, tint = StatusOrange, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Battery Optimization", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Some phone manufacturers aggressively kill background apps. " +
            "Exempting BT Curfew from battery optimization ensures your sleep timer " +
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
