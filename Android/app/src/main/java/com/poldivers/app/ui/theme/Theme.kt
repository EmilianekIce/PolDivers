package com.poldivers.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PolDiversColorScheme = darkColorScheme(
    primary = SuperEarthYellow,
    onPrimary = VoidBlack,
    secondary = SuperEarthYellowDim,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelBorder,
    surfaceContainer = PanelDark,
    surfaceContainerLow = PanelDark,
    surfaceContainerHigh = Color(0xFF1B1F26),
    surfaceContainerHighest = Color(0xFF232833),
    secondaryContainer = Color(0xFF3A3000),
    onSecondaryContainer = SuperEarthYellow,
    outline = PanelBorder,
    outlineVariant = PanelBorder,
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
        // Tighter corners read as military HUD panels rather than soft consumer cards.
        shapes = Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
