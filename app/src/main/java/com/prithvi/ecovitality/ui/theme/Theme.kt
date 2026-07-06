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
    primary = Color(0xFF81C784),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0F1711), // Deep Forest Green Tinted
    surface = Color(0xFF18221B),
    onPrimary = Color(0xFF00390A),
    onSecondary = Color(0xFF003355),
    onTertiary = Color(0xFF4D2B00),
    onBackground = Color(0xFFE1E3DE),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF233027),
    onSurfaceVariant = Color(0xFFC1C9BE),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondaryContainer = Color(0xFF0D47A1),
    onSecondaryContainer = Color(0xFFBBDEFB),
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFFB00020),
    onError = Color.White,
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D9389)
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
    background = Color(0xFFF9FBF9), // Eco Mint Tinted White
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF191C19),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFE0E5DF),
    onSurfaceVariant = Color(0xFF424940),
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF002105),
    secondaryContainer = Color(0xFFD1E4FF),
    onSecondaryContainer = Color(0xFF001D36),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF72796F)
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
