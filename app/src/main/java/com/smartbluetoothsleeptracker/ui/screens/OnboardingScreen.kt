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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.smartbluetoothsleeptracker.R
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
    var showTermsModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ambient Logo Container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
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
                    .size(80.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.height(18.dp))

        // Version badge
        Surface(
            color = AccentBlue.copy(0.12f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                "SLEEPBT v1.0",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AccentBlue,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Welcome to SleepBT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Smart Bluetooth sleep timer & health protector",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Terms box with feature guarantees
        Surface(
            color = Surface1,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TermRow(Icons.Rounded.Security, "100% Offline & Private", "No telemetry or network tracking. All data remains on your device.")
                TermRow(Icons.AutoMirrored.Rounded.VolumeOff, "Smart Playback Fade", "Gradually reduces volume to protect hearing before disconnecting.")
                TermRow(Icons.Rounded.HealthAndSafety, "Ear Health Insights", "Duration estimates provide safety awareness for daily listening.")
            }
        }

        Spacer(Modifier.height(14.dp))

        // View full Terms & Privacy link
        TextButton(
            onClick = { showTermsModal = true }
        ) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Read Full Terms & Privacy Policy",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
        }

        Spacer(Modifier.height(14.dp))

        // Agreement Toggle Card
        Surface(
            color = if (checked) AccentBlue.copy(0.08f) else Surface1,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onCheckedChange(!checked) },
            border = BorderStroke(
                1.dp,
                if (checked) AccentBlue else SurfaceBorder
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
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
                Text(
                    "I agree to the Terms of Service & Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    color = if (checked) TextPrimary else TextSecondary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            enabled = checked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentBlue,
                disabledContainerColor = Surface3
            )
        ) {
            Text("Agree & Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    // Modal Sheet for Terms & Privacy
    if (showTermsModal) {
        AlertDialog(
            onDismissRequest = { showTermsModal = false },
            confirmButton = {
                TextButton(onClick = { showTermsModal = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = AccentBlue)
                }
            },
            title = {
                Text("Terms & Privacy Policy", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(
                    Modifier.heightIn(max = 350.dp)
                ) {
                    Text(
                        "• Offline Operation: SleepBT runs entirely on your device with no remote analytics.\n\n" +
                        "• Bluetooth Control: Uses system reflection APIs to disconnect audio devices on timer expiry.\n\n" +
                        "• Ear Health: Listening duration metrics are for personal awareness and do not constitute medical advice.\n\n" +
                        "• Screen Locking: Optional feature requiring Device Admin permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            },
            containerColor = Surface1,
            shape = RoundedCornerShape(20.dp)
        )
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

@Composable
private fun TermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(Surface3, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
