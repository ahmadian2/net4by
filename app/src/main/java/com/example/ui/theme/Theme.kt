package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CleanWhiteColorScheme = lightColorScheme(
  primary = BrandBlue,
  onPrimary = PureWhite,
  primaryContainer = BrandBlueLight,
  onPrimaryContainer = BrandBlueDark,
  secondary = EmeraldConnected,
  onSecondary = PureWhite,
  secondaryContainer = EmeraldSurface,
  onSecondaryContainer = EmeraldConnected,
  error = CoralStop,
  onError = PureWhite,
  errorContainer = CoralSurface,
  onErrorContainer = CoralStop,
  background = PureWhite,
  onBackground = InkPrimary,
  surface = PureWhite,
  onSurface = InkPrimary,
  surfaceVariant = SoftSlateSurface,
  onSurfaceVariant = InkSecondary,
  outline = SubtleBorder,
  outlineVariant = SubtleBorder
)

private val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PeyvandNetTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = CleanWhiteColorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content
  )
}
