package com.smartbluetoothsleeptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import com.smartbluetoothsleeptracker.data.prefs.AppPrefs
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.ui.navigation.AppNavigation
import com.smartbluetoothsleeptracker.ui.screens.OnboardingScreen
import com.smartbluetoothsleeptracker.ui.screens.OnboardingStep
import com.smartbluetoothsleeptracker.ui.theme.SleepBTTheme
import com.smartbluetoothsleeptracker.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.smartbluetoothsleeptracker.core.firebase.FirebaseManager.initAndTrackDevice(this)

        val app = application as SleepBTApp
        val homeVm = ViewModelProvider(this)[HomeViewModel::class.java]

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState by produceState<AppSettings?>(initialValue = null) {
                app.prefs.settings.collect { value = it }
            }

            if (settingsState != null) {
                val settings = settingsState!!
                val tosValid = settings.tosAcceptedTimestamp > 0L && 
                               settings.tosAcceptedVersion == AppPrefs.CURRENT_TOS_VERSION

                SleepBTTheme(themeMode = settings.themeMode) {
                    if (!tosValid || !settings.onboardingComplete) {
                        OnboardingScreen(
                            initialStep = if (!tosValid) OnboardingStep.TOS else OnboardingStep.NOTIFICATION,
                            onTosAccepted = {
                                scope.launch {
                                    app.prefs.setTosAccepted(
                                        ts = System.currentTimeMillis(),
                                        version = AppPrefs.CURRENT_TOS_VERSION
                                    )
                                }
                            },
                            onComplete = {
                                scope.launch {
                                    app.prefs.setOnboardingComplete(true)
                                }
                            }
                        )
                    } else {
                        AppNavigation(homeViewModel = homeVm)
                    }
                }
            } else {
                // Dark background placeholder during cold start load
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xFF0A0A0C))
                )
            }
        }
    }
}
