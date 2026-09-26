package com.poldivers.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PolDiversColorScheme = darkColorScheme(
    primary = SuperEarthYellow,
    onPrimary = VoidBlack,
    secondary = SuperEarthYellowDim,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelBorder,
    onSurfaceVariant = TextSecondary,
    error = StatusRed,
)

@Composable
fun PolDiversTheme(
    // The app is deliberately dark-only, matching the game's HUD -- a light theme would
    // clash with all the faction/status colors tuned against a near-black background.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PolDiversColorScheme,
        typography = PolDiversTypography,
        content = content,
    )
}
