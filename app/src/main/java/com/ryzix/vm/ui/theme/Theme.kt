package com.ryzix.vm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RyzixPrimary,
    secondary = RyzixSecondary,
    background = RyzixBackground,
    surface = RyzixSurface,
    surfaceVariant = RyzixSurfaceVariant,
    onPrimary = RyzixOnSurface,
    onSecondary = RyzixBackground,
    onBackground = RyzixOnSurface,
    onSurface = RyzixOnSurface,
    onSurfaceVariant = RyzixOnSurfaceVariant,
    error = RyzixRed,
    outline = RyzixBorder
)

@Composable
fun RyzixVMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
