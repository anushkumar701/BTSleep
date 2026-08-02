package com.smartbluetoothsleeptracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartbluetoothsleeptracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            LegalSection("Overview") {
                "SleepBT is a locally-installed Android utility that manages Bluetooth device " +
                "disconnection via sleep timers. This app is distributed as a sideloaded APK and " +
                "is not available on the Google Play Store."
            }

            LegalSection("Data Collection") {
                "SleepBT does NOT collect, transmit, or store any personal data on external servers.\n\n" +
                "All data — including device records, session history, usage statistics, and preferences — " +
                "is stored exclusively on your device using a local Room database and Android DataStore.\n\n" +
                "No analytics, telemetry, crash reports, or usage data is sent to any server. " +
                "There is no network communication of any kind."
            }

            LegalSection("Permissions Used") {
                "• Bluetooth Connect & Scan — Required to detect paired audio devices and disconnect " +
                "them when the sleep timer expires.\n\n" +
                "• Notifications — Used to display timer countdown, warnings before expiry, cooldown " +
                "status, and disconnect results.\n\n" +
                "• Foreground Service — Ensures the timer continues running when the app is backgrounded " +
                "or the screen is off.\n\n" +
                "• Battery Optimization Exemption — Prevents the operating system from killing the " +
                "timer service during sleep.\n\n" +
                "• Notification Listener (optional) — Enables fetching active media sessions to " +
                "pause playback on timer expiry. No notification content is read or stored.\n\n" +
                "• Device Admin (optional) — Enables locking the screen on timer expiry. Can be " +
                "revoked at any time via system settings."
            }

            LegalSection("Bluetooth Actions") {
                "All Bluetooth disconnection and screen-off actions happen entirely " +
                "on your device using Android system APIs. No remote commands are sent or received.\n\n" +
                "The app uses reflection-based APIs for control. These methods operate locally and do not require or establish any network connection."
            }

            LegalSection("Health Data") {
                "The Ear Health feature provides duration-based estimates of listening time. " +
                "It does not measure volume levels or sound pressure. The risk assessments shown " +
                "are approximations based on time only and do not constitute medical advice.\n\n" +
                "All health-related data is stored locally and is never shared."
            }

            LegalSection("Third-Party Services") {
                "SleepBT does not integrate with any third-party analytics, advertising, or " +
                "tracking services. The app operates completely offline."
            }

            LegalSection("Data Deletion") {
                "You can clear all session and usage data from Settings > Usage > Clear All. " +
                "Uninstalling the app removes all stored data from your device."
            }

            LegalSection("Changes") {
                "This privacy policy may be updated in future versions of the app. Any changes " +
                "will be included in the updated APK."
            }

            LegalSection("Contact") {
                "For questions about this privacy policy, contact the developer via the repository " +
                "where this app is distributed."
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            LegalSection("Acceptance") {
                "By installing and using SleepBT, you agree to these Terms of Service. " +
                "If you do not agree, uninstall the app immediately."
            }

            LegalSection("Description of Service") {
                "SleepBT is a personal utility application that:\n\n" +
                "• Runs a configurable sleep timer\n" +
                "• Disconnects paired Bluetooth audio devices when the timer expires\n" +
                "• Optionally stops media playback with a gradual volume fade\n" +
                "• Optionally locks the screen on timer expiry\n" +
                "• Tracks listening duration for ear health awareness\n\n" +
                "The app is designed for personal, non-commercial use and is distributed " +
                "as a sideloaded APK only."
            }

            LegalSection("Local-Only Operation") {
                "All data processing, storage, and device control actions happen entirely on your " +
                "device. SleepBT has no server infrastructure, no cloud storage, and makes no " +
                "network requests. Your data never leaves your device."
            }

            LegalSection("Bluetooth & System Control") {
                "This app uses reflection-based Android APIs to " +
                "control Bluetooth connections. These methods:\n\n" +
                "• May not work on all devices or OEM Android skins\n" +
                "• Are not guaranteed by Google or your device manufacturer\n" +
                "• May break with future Android updates\n\n" +
                "The app makes a best-effort attempt to disconnect devices but cannot guarantee " +
                "success on every hardware and software combination."
            }

            LegalSection("Health Disclaimer") {
                "The Ear Health feature is NOT medical advice. It provides rough estimates of " +
                "listening duration only. It does not measure volume levels, sound pressure, or " +
                "any physiological data.\n\n" +
                "Consult a healthcare professional for concerns about hearing health."
            }

            LegalSection("No Warranty") {
                "SleepBT is provided \"as is\" without warranty of any kind, express or implied. " +
                "The developer is not liable for any damages arising from the use of this app, " +
                "including but not limited to missed alarms, failed disconnections, or any " +
                "disruption to device functionality."
            }

            LegalSection("Device Admin") {
                "If you grant Device Admin privileges to this app for the screen-off feature, " +
                "you can revoke them at any time via:\n" +
                "Settings > Security > Device admin apps > SleepBT\n\n" +
                "The app only uses the force-lock policy and does not set passwords, " +
                "wipe data, or perform any other admin actions."
            }

            LegalSection("Intellectual Property") {
                "© 2026 DreamSync. All rights reserved.\n\n" +
                "This app and its source code are the property of the developer. " +
                "Redistribution without permission is not authorized."
            }

            LegalSection("Changes") {
                "These terms may be updated in future versions of the app. Continued use " +
                "after an update constitutes acceptance of the revised terms."
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, content: () -> String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            content(),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 18.sp
        )
    }
}
