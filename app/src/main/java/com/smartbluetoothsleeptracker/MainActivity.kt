package com.smartbluetoothsleeptracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.service.SleepTimerService
import com.smartbluetoothsleeptracker.ui.navigation.AppNavigation
import com.smartbluetoothsleeptracker.ui.screens.OnboardingScreen
import com.smartbluetoothsleeptracker.ui.theme.SleepBTTheme
import com.smartbluetoothsleeptracker.viewmodel.*

class MainActivity : ComponentActivity() {

    private val app by lazy { application as SleepBTApp }

    private val homeVm    by lazy { makeVm { HomeViewModel(app.timerManager, app.btMonitor, app.btDisconnector, app.prefs) } }
    private val historyVm by lazy { makeVm { HistoryViewModel(app.db) } }
    private val settingsVm by lazy { makeVm { SettingsViewModel(app.prefs) } }
    private val healthVm  by lazy { makeVm { HealthViewModel(app.db) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsVm.settings.collectAsStateWithLifecycle()

            // ── Permission launcher ──────────────────────────────────────────
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* results handled reactively by the OS */ }

            // ── Notification permission — separate launcher for better UX ────
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* granted or denied — service will still work without it */ }

            SleepBTTheme(themeMode = settings.themeMode) {

                if (!settings.onboardingComplete) {
                    // ── FIRST LAUNCH: Onboarding ─────────────────────────────
                    OnboardingScreen(onComplete = {
                        settingsVm.completeOnboarding()
                        // Request BT + location permissions
                        val missing = btPermissions().filter { !isGranted(it) }
                        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
                        // Request POST_NOTIFICATIONS separately (better system dialog)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    })
                } else {
                    // ── NORMAL LAUNCH: Check for any missing permissions ──────
                    LaunchedEffect(Unit) {
                        val missing = btPermissions().filter { !isGranted(it) }
                        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
                    }
                    // Notification permission — shown once if not granted
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    AppNavigation(
                        homeVm    = homeVm,
                        historyVm = historyVm,
                        settingsVm = settingsVm,
                        healthVm  = healthVm,
                        onStartTimer = { minutes ->
                            SleepTimerService.startTimerIfAllowed(this, minutes)
                        },
                        onCancelTimer = {
                            ContextCompat.startForegroundService(this, SleepTimerService.cancelIntent(this))
                        },
                        onExtendTimer = {
                            app.timerManager.extendTimer(10L)
                            ContextCompat.startForegroundService(this, SleepTimerService.startIntent(this))
                        }
                    )
                }
            }
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun btPermissions() = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private inline fun <reified T : ViewModel> makeVm(crossinline factory: () -> T): T {
        return ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(c: Class<VM>): VM {
                @Suppress("UNCHECKED_CAST") return factory() as VM
            }
        })[T::class.java]
    }
}
