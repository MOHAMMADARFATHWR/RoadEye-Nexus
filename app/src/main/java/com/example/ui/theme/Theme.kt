package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CockpitDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = AmberTelemetry,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberTelemetry,
    tertiary = SignalEmerald,
    onTertiary = OnEmerald,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = SignalEmerald,
    error = AlertCrimson,
    onError = OnCrimson,
    errorContainer = CrimsonContainer,
    onErrorContainer = AlertCrimson,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CockpitOutline,
    outlineVariant = CockpitOutline,
)

private val PureOledDarkColorScheme = CockpitDarkColorScheme.copy(
    background = Color.Black,
    surface = Color(0xFF06080C),
    surfaceVariant = Color(0xFF0E131B),
)

@Composable
fun RoadEyeNexusTheme(
    pureOledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (pureOledBlack) PureOledDarkColorScheme else CockpitDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
