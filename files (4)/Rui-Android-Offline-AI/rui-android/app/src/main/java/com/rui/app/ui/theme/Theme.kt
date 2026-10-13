package com.rui.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours sampled from the Rui desktop UI and logo. */
private val RuiBlue = Color(0xFF4F6BFF)
private val RuiPurple = Color(0xFF9B5CF6)
private val RuiLilac = Color(0xFFB794F6)
val RuiAmber = Color(0xFFF5B83D)
val RuiGreen = Color(0xFF34D399)

private val RuiDark = darkColorScheme(
    primary = RuiBlue, onPrimary = Color.White,
    secondary = RuiLilac, tertiary = RuiPurple,
    background = Color(0xFF0A0A1C), onBackground = Color(0xFFE8EAF6),
    surface = Color(0xFF0A0A1C), onSurface = Color(0xFFE8EAF6),
    onSurfaceVariant = Color(0xFF8A8DB0),
    surfaceContainerLow = Color(0xFF0D0D22),
    surfaceContainer = Color(0xFF14142D),
    surfaceContainerHigh = Color(0xFF1B1B3A),
    outline = Color(0xFF2A2A4A), outlineVariant = Color(0xFF1F1F3C),
)

private val RuiTypography = Typography(
    headlineLarge = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private val RuiShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/** Blue → purple gradient used by the greeting, composer border and send button. */
@Composable
fun brandBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.horizontalGradient(listOf(cs.primary, cs.tertiary))
}

/**
 * Rui's own dark palette by default so the app matches the desktop design.
 * Pass dynamicColor = true to follow the wallpaper (Material You) on Android 12+.
 */
@Composable
fun RuiTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = if (dynamicColor) isSystemInDarkTheme() else true
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> RuiDark
    }
    MaterialTheme(colorScheme = scheme, typography = RuiTypography, shapes = RuiShapes, content = content)
}
