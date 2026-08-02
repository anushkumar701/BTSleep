package com.smartbluetoothsleeptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.ui.navigation.AppNavigation
import com.smartbluetoothsleeptracker.ui.screens.OnboardingScreen
import com.smartbluetoothsleeptracker.ui.theme.SleepBTTheme
import com.smartbluetoothsleeptracker.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SleepBTApp
        val homeVm = ViewModelProvider(this)[HomeViewModel::class.java]

        setContent {
            val scope = rememberCoroutineScope()
            val settingsState by produceState<AppSettings?>(initialValue = null) {
                app.prefs.settings.collect { value = it }
            }

            if (settingsState != null) {
                val settings = settingsState!!
                SleepBTTheme(themeMode = settings.themeMode) {
                    if (!settings.onboardingComplete) {
                        OnboardingScreen(
                            onComplete = {
                                scope.launch {
                                    app.prefs.setOnboardingComplete(true)
                                    app.prefs.setTosAccepted(System.currentTimeMillis())
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
