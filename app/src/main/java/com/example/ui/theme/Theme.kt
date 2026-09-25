package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StudioPrimaryCyan,
    onPrimary = Color.Black,
    secondary = StudioSecondary,
    onSecondary = Color.Black,
    tertiary = StudioPrimaryBlue,
    background = StudioDarkBackground,
    onBackground = StudioTextPrimary,
    surface = StudioDarkSurface,
    onSurface = StudioTextPrimary,
    error = StudioAccentRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
