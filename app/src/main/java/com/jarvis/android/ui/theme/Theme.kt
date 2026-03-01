package com.jarvis.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Always dark — no light mode
private val JarvisColorScheme = darkColorScheme(
    background          = ObsidianBg,
    surface             = ObsidianSurface,
    surfaceVariant      = ObsidianVariant,
    surfaceContainerLow = ObsidianVariant,
    primary             = Gold,
    onPrimary           = ObsidianBg,
    primaryContainer    = GoldDim,
    onPrimaryContainer  = Gold,
    secondary           = GoldMuted,
    onSecondary         = TextPrimary,
    onBackground        = TextPrimary,
    onSurface           = TextPrimary,
    onSurfaceVariant    = TextSecondary,
    outline             = ObsidianBorder,
    outlineVariant      = ObsidianVariant,
    error               = Destructive,
    onError             = Color.White,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ObsidianBg.toArgb()
            window.navigationBarColor = ObsidianBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
