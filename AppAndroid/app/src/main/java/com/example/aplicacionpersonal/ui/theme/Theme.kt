package com.example.aplicacionpersonal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoLight,
    onPrimaryContainer = DeepNavy,
    secondary = DeepNavy,
    secondaryContainer = Color(0xFFEAE8EF),
    onSecondaryContainer = DeepNavy,
    tertiary = Sage,
    background = WarmBackground,
    surface = Color.White,
    onBackground = DeepNavy,
    onSurface = DeepNavy
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3C2FF),
    primaryContainer = Color(0xFF3D3C91),
    secondaryContainer = Color(0xFF302F39),
    tertiary = Color(0xFF8ECDB3),
    background = DarkBackground,
    surface = DarkSurface
)

@Composable
fun AplicacionPersonalTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
