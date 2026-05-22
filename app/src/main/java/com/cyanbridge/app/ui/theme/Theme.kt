package com.cyanbridge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBackground,
    primaryContainer = CyanPrimaryDark,
    onPrimaryContainer = CyanSecondary,
    secondary = CyanSecondary,
    onSecondary = DarkBackground,
    tertiary = CyanTertiary,
    background = DarkBackground,
    onBackground = OnDarkPrimary,
    surface = DarkSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    error = StatusError,
    onError = OnDarkPrimary
)

@Composable
fun CyanBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CyanTypography,
        content = content
    )
}
