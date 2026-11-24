package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ApexDarkColorScheme = darkColorScheme(
    primary = AiCyan,
    onPrimary = ObsidianBg,
    primaryContainer = AiCyanMuted,
    onPrimaryContainer = TextPrimary,
    secondary = BullEmerald,
    onSecondary = ObsidianBg,
    secondaryContainer = BullEmeraldMuted,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberGold,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberGoldMuted,
    onTertiaryContainer = TextPrimary,
    error = BearCrimson,
    onError = TextPrimary,
    errorContainer = BearCrimsonMuted,
    onErrorContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = SlateHighlight
)

private val ApexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ApexDeskTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = ApexTypography,
        shapes = ApexShapes,
        content = content
    )
}
