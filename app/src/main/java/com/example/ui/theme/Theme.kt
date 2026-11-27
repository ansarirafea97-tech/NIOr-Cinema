package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CinemaPrimary,
    onPrimary = CinemaOnPrimary,
    primaryContainer = CinemaPrimaryContainer,
    onPrimaryContainer = CinemaOnPrimaryContainer,
    secondary = CinemaSecondary,
    secondaryContainer = CinemaSecondaryContainer,
    onSecondaryContainer = CinemaOnSecondaryContainer,
    background = CinemaBackground,
    surface = CinemaSurface,
    onSurface = CinemaOnSurface,
    onSurfaceVariant = CinemaOnSurfaceVariant,
    outline = CinemaOutline
)

@Composable
fun MyApplicationTheme(
    appTheme: com.example.viewmodel.AppTheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (appTheme != null) {
        darkColorScheme(
            primary = appTheme.primaryColor,
            onPrimary = appTheme.onPrimaryColor,
            primaryContainer = appTheme.primaryContainer,
            onPrimaryContainer = appTheme.primaryColor,
            secondary = appTheme.primaryColor,
            secondaryContainer = appTheme.primaryContainer,
            onSecondaryContainer = appTheme.primaryColor,
            background = CinemaBackground,
            surface = CinemaSurface,
            onSurface = CinemaOnSurface,
            onSurfaceVariant = CinemaOnSurfaceVariant,
            outline = CinemaOutline
        )
    } else {
        DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
