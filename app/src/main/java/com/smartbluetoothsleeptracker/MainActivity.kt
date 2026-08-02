package com.smartbluetoothsleeptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartbluetoothsleeptracker.data.prefs.AppSettings
import com.smartbluetoothsleeptracker.ui.navigation.AppNavigation
import com.smartbluetoothsleeptracker.ui.screens.OnboardingScreen
import com.smartbluetoothsleeptracker.ui.theme.BTCurfewTheme
import com.smartbluetoothsleeptracker.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BTCurfewApp
        val homeVm = ViewModelProvider(this)[HomeViewModel::class.java]

        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )

            BTCurfewTheme(themeMode = settings.themeMode) {
                if (!settings.onboardingComplete) {
                    OnboardingScreen(
                        onComplete = {
                            kotlinx.coroutines.MainScope().launch {
                                app.prefs.setOnboardingComplete(true)
                                app.prefs.setTosAccepted(System.currentTimeMillis())
                            }
                        }
                    )
                } else {
                    AppNavigation(homeViewModel = homeVm)
                }
            }
        }
    }
}
