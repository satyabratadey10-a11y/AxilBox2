package com.axilbox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = PrimaryHover,
    secondary = PrimaryHover,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceSecondary,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusBootingColor,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = StatusErrorColor,
    onError = Color.White
)

@Composable
fun AxilBoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AxilBoxTypography,
        shapes = AxilBoxShapes,
        content = content
    )
}
