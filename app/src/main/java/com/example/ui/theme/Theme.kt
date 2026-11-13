package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class PulseSemanticColors(
  val income: Color,
  val expense: Color,
  val warning: Color,
  val cardBorder: Color,
  val mutedText: Color,
  val pillBackground: Color,
  val heroStart: Color,
  val heroEnd: Color,
)

private val LightSemanticColors = PulseSemanticColors(
  income = Emerald600,
  expense = Rose600,
  warning = Amber600,
  cardBorder = Slate200,
  mutedText = Slate500,
  pillBackground = Slate100,
  heroStart = Indigo600,
  heroEnd = Violet600,
)

private val DarkSemanticColors = PulseSemanticColors(
  income = Emerald400,
  expense = Rose400,
  warning = Amber400,
  cardBorder = Slate800,
  mutedText = Slate400,
  pillBackground = Slate800,
  heroStart = Indigo600,
  heroEnd = Violet600,
)

val LocalPulseColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
  primary = Indigo600,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0E7FF),
  onPrimaryContainer = Color(0xFF1E1B4B),
  secondary = Violet600,
  onSecondary = Color.White,
  background = Slate50,
  onBackground = Slate900,
  surface = Color.White,
  onSurface = Slate900,
  surfaceVariant = Slate100,
  onSurfaceVariant = Slate600,
  outline = Slate200,
  error = Rose600,
  onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
  primary = Indigo400,
  onPrimary = Slate950,
  primaryContainer = Color(0xFF312E81),
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = Violet400,
  onSecondary = Slate950,
  background = Slate950,
  onBackground = Slate50,
  surface = Slate900,
  onSurface = Slate50,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate400,
  outline = Slate800,
  error = Rose400,
  onError = Slate950,
)

private val PulseShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PulseTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalPulseColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = PulseTypography,
      shapes = PulseShapes,
      content = content,
    )
  }
}
