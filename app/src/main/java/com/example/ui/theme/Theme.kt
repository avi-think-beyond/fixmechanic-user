package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
  primary = Navy900,
  onPrimary = SurfaceWhite,
  primaryContainer = Navy800,
  onPrimaryContainer = SurfaceWhite,
  secondary = OnlineGreen,
  onSecondary = SurfaceWhite,
  secondaryContainer = OnlineGreenLight,
  onSecondaryContainer = OnlineGreenDark,
  tertiary = EmergencyRed,
  onTertiary = SurfaceWhite,
  tertiaryContainer = EmergencyRedLight,
  onTertiaryContainer = EmergencyRedDark,
  error = EmergencyRed,
  onError = SurfaceWhite,
  errorContainer = EmergencyRedLight,
  onErrorContainer = EmergencyRedDark,
  background = SurfaceWhite,
  onBackground = Navy900,
  surface = SurfaceWhite,
  onSurface = Navy900,
  surfaceVariant = SurfaceVariantSlate,
  onSurfaceVariant = Navy700,
  outline = BorderLight,
  outlineVariant = DividerColor
)

private val DarkColorScheme = darkColorScheme(
  primary = SurfaceWhite,
  onPrimary = Navy900,
  primaryContainer = Navy800,
  onPrimaryContainer = SurfaceWhite,
  secondary = OnlineGreen,
  onSecondary = Navy900,
  secondaryContainer = OnlineGreenDark,
  onSecondaryContainer = SurfaceWhite,
  tertiary = EmergencyRed,
  onTertiary = SurfaceWhite,
  tertiaryContainer = EmergencyRedDark,
  onTertiaryContainer = EmergencyRedLight,
  error = EmergencyRed,
  onError = SurfaceWhite,
  background = NavyDark,
  onBackground = SurfaceWhite,
  surface = Navy900,
  onSurface = SurfaceWhite,
  surfaceVariant = Navy800,
  onSurfaceVariant = Navy100,
  outline = Navy700,
  outlineVariant = Navy800
)

@Composable
fun FixMechanicTheme(
  darkTheme: Boolean = false, // Mechanic app prioritizes professional clean white automotive interface by default
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        window.statusBarColor = SurfaceWhite.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
