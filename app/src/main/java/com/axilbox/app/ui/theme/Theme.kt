package com.axilbox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PureBlackColorScheme = darkColorScheme(
    primary = ButtonWhite,
    onPrimary = ButtonTextBlack,
    primaryContainer = BackgroundDark,
    onPrimaryContainer = TextPrimary,
    secondary = ButtonWhite,
    onSecondary = ButtonTextBlack,
    secondaryContainer = BackgroundDark,
    onSecondaryContainer = TextPrimary,
    tertiary = ButtonWhite,
    onTertiary = ButtonTextBlack,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSecondary,
    onSurfaceVariant = TextSecondary,
    outline = BorderWhite,
    outlineVariant = BorderWhite,
    error = ButtonWhite,
    onError = ButtonTextBlack
)

@Composable
fun AxilBoxTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PureBlackColorScheme,
        typography = AxilBoxTypography,
        shapes = AxilBoxShapes,
        content = content
    )
}
