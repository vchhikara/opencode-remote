package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OpenCodeColorScheme = darkColorScheme(
    primary = OpenCodePrimary,
    secondary = OpenCodeSecondary,
    tertiary = OpenCodeTertiary,
    background = OpenCodeBackground,
    surface = OpenCodeSurface,
    surfaceVariant = OpenCodeSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = TextPrimary,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MyApplicationTheme(
    // We force dark theme for the "OpenCode hacker" vibe
    darkTheme: Boolean = true,
    // We disable dynamic color to preserve the specific branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = OpenCodeColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // window.statusBarColor and window.navigationBarColor are deprecated
            // Edge-to-edge is enabled in MainActivity
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
