package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OneUIBlue,
    onPrimary = Color.White,
    secondary = OneUIGray,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    outline = Color(0xFF333333),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Colors.primary,
    onPrimary = Colors.primaryText,
    secondary = Colors.textSecondary,
    background = Colors.background,
    surface = Colors.surface,
    outline = Colors.border,
    onBackground = Colors.textPrimary,
    onSurface = Colors.textPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
