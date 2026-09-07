package com.tihon.productivnost.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object P {
    val ink = Color(0xFF080B0A)
    val bg = Color(0xFF0C1210)
    val surface = Color(0xFF131B18)
    val surfaceHigh = Color(0xFF1B2521)
    val outline = Color(0xFF27332E)

    val leaf = Color(0xFF7BD88F)
    val leafDeep = Color(0xFF3E9C63)
    val sky = Color(0xFF74B6E8)
    val amber = Color(0xFFE7B770)
    val rose = Color(0xFFE88C9A)
    val violet = Color(0xFFB49BE8)

    val text = Color(0xFFE9F1EC)
    val textDim = Color(0xFF9DAEA5)
    val textFaint = Color(0xFF6B7C74)
}

/** Дополнительные цвета, которых нет в Material-схеме. */
data class Extra(
    val bgTop: Color = Color(0xFF101816),
    val bgBottom: Color = Color(0xFF080C0B),
    val card: Color = P.surface,
    val cardHigh: Color = P.surfaceHigh,
    val outline: Color = P.outline,
    val dim: Color = P.textDim,
    val faint: Color = P.textFaint,
    val study: Color = P.sky,
    val focus: Color = P.leaf,
    val warn: Color = P.amber,
    val danger: Color = P.rose,
)

val LocalExtra = staticCompositionLocalOf { Extra() }

private val scheme = darkColorScheme(
    primary = P.leaf,
    onPrimary = Color(0xFF06110A),
    primaryContainer = Color(0xFF1D3527),
    onPrimaryContainer = P.leaf,
    secondary = P.sky,
    onSecondary = Color(0xFF04121C),
    secondaryContainer = Color(0xFF16303F),
    onSecondaryContainer = P.sky,
    tertiary = P.amber,
    onTertiary = Color(0xFF1A1206),
    background = P.bg,
    onBackground = P.text,
    surface = P.surface,
    onSurface = P.text,
    surfaceVariant = P.surfaceHigh,
    onSurfaceVariant = P.textDim,
    outline = P.outline,
    outlineVariant = Color(0xFF1E2925),
    error = P.rose,
    onError = Color(0xFF1B0709),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val typo = Typography(
    displayLarge = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.6).sp, lineHeight = 58.sp),
    displayMedium = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.2).sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun ProductivnostTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtra provides Extra()) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typo, content = content)
    }
}
