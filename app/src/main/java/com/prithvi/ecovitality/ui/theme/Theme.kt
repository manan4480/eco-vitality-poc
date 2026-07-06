package com.prithvi.ecovitality.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondaryContainer = Color(0xFF0D47A1),
    onSecondaryContainer = Color(0xFF90CAF9),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color(0xFFFFCDD2),
    outline = Color(0xFF666666)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFFFB74D),
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFEEEEEE),
    primaryContainer = Color(0xFF002200),
    onPrimaryContainer = Color(0xFF81C784),
    secondaryContainer = Color(0xFF001122),
    onSecondaryContainer = Color(0xFF64B5F6),
    errorContainer = Color(0xFF330000),
    onErrorContainer = Color(0xFFFF5555),
    outline = Color(0xFF444444)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    outline = Color(0xFF79747E)
)

@Composable
fun EcoVitalityTheme(
    themeMode: String = "System",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        "AMOLED" -> AmoledColorScheme
        "Dark" -> DarkColorScheme
        "Light" -> LightColorScheme
        else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
