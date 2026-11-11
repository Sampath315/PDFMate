package com.example.pdfmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E5),
    onPrimaryContainer = BrandDeep,
    secondary = BrandOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8DD),
    onSecondaryContainer = Color(0xFF7A2E10),
    tertiary = EditViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE6FF),
    onTertiaryContainer = Color(0xFF3A1D86),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceMid,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceMid,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDADFEA),
    outline = LightOutline,
    outlineVariant = Color(0xFFE4E8F0),
    inverseSurface = Color(0xFF1C2230),
    inverseOnSurface = Color(0xFFF1F3F8),
    error = Color(0xFFD92D20),
    scrim = Color(0xFF0B0F17)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF5A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1620),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = BrandOrange,
    onSecondary = Color(0xFF2A0E00),
    secondaryContainer = Color(0xFF4A2414),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1D0F4A),
    tertiaryContainer = Color(0xFF2E2156),
    onTertiaryContainer = Color(0xFFE6DEFF),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceMid,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainerLowest = Color(0xFF0A0E14),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceMid,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2B3342),
    outline = DarkOutline,
    outlineVariant = Color(0xFF242B37),
    inverseSurface = Color(0xFFE8EBF2),
    inverseOnSurface = Color(0xFF151A23),
    error = Color(0xFFFF6B5E),
    scrim = Color.Black
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun PDFMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
