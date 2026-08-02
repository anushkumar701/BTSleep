package com.smartbluetoothsleeptracker.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextOnAccent,
    primaryContainer = AccentBlue.copy(alpha = 0.15f),
    secondary = AccentPurple,
    tertiary = AccentCyan,
    background = DeepBlack,
    surface = Surface1,
    surfaceVariant = Surface2,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = StatusRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlue.copy(alpha = 0.1f),
    secondary = AccentPurple,
    tertiary = AccentCyan,
    background = LightBg,
    surface = LightSurface1,
    surfaceVariant = LightSurface2,
    onBackground = LightTextPri,
    onSurface = LightTextPri,
    onSurfaceVariant = LightTextSec,
    outline = LightBorder,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun BTCurfewTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true // "DARK" default
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = findActivity(view.context)
            activity?.window?.let { window ->
                window.statusBarColor = (if (isDark) DeepBlack else LightBg).toArgb()
                window.navigationBarColor = (if (isDark) DeepBlack else LightBg).toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
