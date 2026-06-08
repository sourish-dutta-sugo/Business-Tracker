package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = LocalAppTheme.current,
    content: @Composable () -> Unit
) {
    val colorScheme = if (appTheme == AppTheme.DARK) {
        darkColorScheme(
            primary = appTheme.accentPrimary,
            onPrimary = Color.Black,
            secondary = appTheme.textSecondary,
            background = appTheme.backgroundPrimary,
            surface = appTheme.backgroundSecondary,
            outline = AppColors.border,
            onBackground = appTheme.textPrimary,
            onSurface = appTheme.textPrimary
        )
    } else {
        lightColorScheme(
            primary = appTheme.accentPrimary,
            onPrimary = AppColors.textOnPrimary,
            secondary = appTheme.textSecondary,
            background = appTheme.backgroundPrimary,
            surface = appTheme.backgroundSecondary,
            outline = AppColors.border,
            onBackground = appTheme.textPrimary,
            onSurface = appTheme.textPrimary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
