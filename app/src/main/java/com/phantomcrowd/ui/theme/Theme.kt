package com.phantomcrowd.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light-first branded color scheme.
 * Dynamic color is disabled so the app always shows the curated palette.
 */
private val PhantomLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = SecondaryTeal,
    onSecondary = OnSecondary,
    background = BackgroundLight,
    surface = SurfaceWhite,
    surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = NeutralMuted,
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = InverseOnSurface,
    error = ErrorRed,
    outline = OutlineLight,
    outlineVariant = OutlineLight
)

@Composable
fun PhantomCrowdTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = PhantomLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Light background → dark status bar icons
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
