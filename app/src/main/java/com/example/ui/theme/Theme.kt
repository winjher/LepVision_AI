package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoftContainer,
    onPrimaryContainer = OnEmeraldSoftContainer,
    secondary = BirdwingGold,
    onSecondary = Color.White,
    secondaryContainer = BirdwingGoldContainer,
    onSecondaryContainer = OnBirdwingGoldContainer,
    tertiary = SwallowtailTeal,
    onTertiary = Color.White,
    tertiaryContainer = SwallowtailTealContainer,
    onTertiaryContainer = OnSwallowtailTealContainer,
    error = LacewingCrimson,
    onError = Color.White,
    errorContainer = LacewingCrimsonContainer,
    onErrorContainer = OnLacewingCrimsonContainer,
    background = IvoryBackground,
    onBackground = ForestInk,
    surface = IvorySurface,
    onSurface = ForestInk,
    surfaceVariant = SageSurfaceVariant,
    onSurfaceVariant = OnSageSurfaceVariant,
    outline = SageOutline,
    outlineVariant = SageOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = OnEmeraldMint,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldSoftContainer,
    secondary = BirdwingGoldLight,
    onSecondary = OnBirdwingGoldContainer,
    secondaryContainer = BirdwingAmberDarkContainer,
    onSecondaryContainer = BirdwingGoldContainer,
    tertiary = SwallowtailTealMint,
    onTertiary = OnSwallowtailTealContainer,
    tertiaryContainer = SwallowtailTealDarkContainer,
    onTertiaryContainer = SwallowtailTealContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = LacewingCrimsonContainer,
    background = CanopyNightBackground,
    onBackground = CanopyInk,
    surface = CanopyNightSurface,
    onSurface = CanopyInk,
    surfaceVariant = CanopyNightSurfaceVariant,
    onSurfaceVariant = CanopyInkMuted,
    outline = CanopyOutline,
    outlineVariant = CanopyOutlineVariant
)

val LepVisionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LepVisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LepVisionTypography,
        shapes = LepVisionShapes,
        content = content
    )
}
