package com.smartbluetoothsleeptracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = TextOnAccent,
    primaryContainer = SpaceSurface2,
    onPrimaryContainer = TextPrimary,
    secondary        = AccentPurple,
    onSecondary      = TextOnAccent,
    background       = DeepSpace,
    onBackground     = TextPrimary,
    surface          = SpaceSurface,
    onSurface        = TextPrimary,
    surfaceVariant   = SpaceSurface2,
    onSurfaceVariant = TextSecondary,
    outline          = SpaceSurfaceHigh,
    error            = ErrorRed,
    onError          = TextOnAccent
)

private val LightColorScheme = lightColorScheme(
    primary          = AccentBlue,
    onPrimary        = TextOnAccent,
    background       = androidx.compose.ui.graphics.Color(0xFFF0F4FF),
    onBackground     = androidx.compose.ui.graphics.Color(0xFF0A0A1A),
    surface          = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface        = androidx.compose.ui.graphics.Color(0xFF0A0A1A),
    surfaceVariant   = androidx.compose.ui.graphics.Color(0xFFE8EEFF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A5568),
    error            = ErrorRed
)

@Composable
fun SleepBTTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK"  -> true
        else    -> isSystemInDarkTheme() // AUTO
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
