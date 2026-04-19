package com.parc.fitnesstimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = Md3Primary,
    onPrimary        = Md3OnPrimary,
    secondary        = Md3Secondary,
    onSecondary      = Md3OnSecondary,
    background       = Md3Background,
    onBackground     = Md3OnBackground,
    surface          = Md3Surface,
    onSurface        = Md3OnSurface,
    surfaceVariant   = Md3SurfaceVariant,
    onSurfaceVariant = Md3OnSurfaceVar,
    outline          = Md3Outline,
    error            = Md3Error,
    onError          = Md3OnError
)

/**
 * PARC Fitness Timer app theme.
 * Forces dark mode — the app is designed exclusively for dark environments
 * matching the gym timer's physical aesthetic.
 */
@Composable
fun ParcFitnessTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = ParcTypography,
        content     = content
    )
}
