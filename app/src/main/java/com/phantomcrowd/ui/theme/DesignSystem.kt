package com.phantomcrowd.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phantom Crowd Design System — Light-first, trust-focused tokens.
 *
 * All colors, typography, shapes, spacing, elevation, and motion tokens
 * are centralised here. Every Compose component in the app should
 * reference these tokens instead of hard-coding values.
 */
object DesignSystem {

    // ═══════════════════════ COLORS ═══════════════════════

    object Colors {
        // Brand
        val primary          = Color(0xFF1767D1)
        val onPrimary        = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFE6F0FF)

        val secondary        = Color(0xFF029F87)
        val onSecondary      = Color(0xFFFFFFFF)

        // Surfaces
        val background       = Color(0xFFFAFBFD)
        val surface          = Color(0xFFFFFFFF)
        val surfaceVariant   = Color(0xFFF3F6FA)
        val onSurface        = Color(0xFF0F1720)
        val inverseOnSurface = Color(0xFFEBF2FF)

        // Feedback
        val error            = Color(0xFFD93025)
        val success          = Color(0xFF0F9D58)
        val warning          = Color(0xFFFFB300)

        // Severity
        val severityHigh     = Color(0xFFD6453D)
        val severityMedium   = Color(0xFFE67A00)
        val severityLow      = Color(0xFF2E9B5D)

        // Heatmap
        val heatmapRed       = Color(0xFFD32F2F)
        val heatmapYellow    = Color(0xFFFBC02D)
        val heatmapGreen     = Color(0xFF43A047)

        // Neutral / Utility
        val neutralMuted     = Color(0xFF6B7280)
        val outline          = Color(0xFFE6EEF8)
        val link             = Color(0xFF0B63D8)
    }

    // ═══════════════════ TYPOGRAPHY ═══════════════════════

    // Inter preferred, Roboto fallback (both available on most Android devices)
    val fontFamily: FontFamily = FontFamily.Default  // Uses system Roboto

    object Typography {
        val displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp
        )
        val headlineMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp
        )
        val titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 26.sp
        )
        val bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        )
        val bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        )
        val labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    }

    // ═══════════════════ SHAPES ═══════════════════════════

    object Shapes {
        val card = RoundedCornerShape(12.dp)
        val chip = RoundedCornerShape(10.dp)
        val pill = RoundedCornerShape(24.dp)
        val dialog = RoundedCornerShape(16.dp)
        val bottomSheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    }

    // ═══════════════════ SPACING ═══════════════════════════

    object Spacing {
        val xxs: Dp = 4.dp
        val xs: Dp  = 8.dp
        val sm: Dp  = 12.dp
        val md: Dp  = 16.dp
        val lg: Dp  = 24.dp
        val xl: Dp  = 32.dp
        val xxl: Dp = 48.dp
    }

    // ═══════════════════ ELEVATION ═════════════════════════

    object Elevation {
        val none: Dp      = 0.dp
        val smallCard: Dp = 2.dp
        val card: Dp      = 4.dp
        val dialog: Dp    = 8.dp
    }

    // ═══════════════════ MOTION ════════════════════════════

    object Motion {
        const val standardDurationMs = 180
        const val urgentPulseDurationMs = 750
        const val urgentPulseScale = 1.04f

        fun <T> standardTween() = tween<T>(
            durationMillis = standardDurationMs,
            easing = FastOutSlowInEasing
        )
    }
}
