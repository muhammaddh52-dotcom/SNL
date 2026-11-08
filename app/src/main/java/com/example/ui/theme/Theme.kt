package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Represents the user's selected theme preference:
 * - [SYSTEM]: Automatically adapts to Android system-wide dark mode (`isSystemInDarkTheme()`).
 * - [LIGHT]: Forces Light theme.
 * - [DARK]: Forces Dark theme.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

val SnlDarkColorScheme = darkColorScheme(
    primary = SnlRed,
    onPrimary = SnlWhite,
    primaryContainer = SnlNavyLight,
    onPrimaryContainer = SnlYellow,
    secondary = SnlYellow,
    onSecondary = SnlBlack,
    secondaryContainer = SnlCardDark,
    onSecondaryContainer = SnlWhite,
    tertiary = SnlNavyLight,
    onTertiary = SnlWhite,
    background = SnlSurfaceDark,
    onBackground = SnlWhite,
    surface = SnlCardDark,
    onSurface = SnlWhite,
    surfaceVariant = Color(0xFF1F2F46),
    onSurfaceVariant = Color(0xFFD0D8E4),
    error = Color(0xFFFF6B6B),
    onError = SnlBlack
)

val SnlLightColorScheme = lightColorScheme(
    primary = SnlNavy,
    onPrimary = SnlWhite,
    primaryContainer = Color(0xFFE2EBF5),
    onPrimaryContainer = SnlNavy,
    secondary = SnlRed,
    onSecondary = SnlWhite,
    secondaryContainer = Color(0xFFFCE8E9),
    onSecondaryContainer = SnlRedDark,
    tertiary = SnlYellowDark,
    onTertiary = SnlBlack,
    background = SnlSurfaceLight,
    onBackground = SnlBlack,
    surface = SnlWhite,
    onSurface = SnlBlack,
    surfaceVariant = Color(0xFFEBF0F7),
    onSurfaceVariant = Color(0xFF3A4859),
    error = SnlRedDark,
    onError = SnlWhite
)

@Composable
fun resolveSnlColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme {
    val context = LocalContext.current
    return when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SnlDarkColorScheme
        else -> SnlLightColorScheme
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        null -> darkTheme
    }
    val colorScheme = resolveSnlColorScheme(
        darkTheme = effectiveDark,
        dynamicColor = dynamicColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun SNLLubricantsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: AppThemeMode? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    MyApplicationTheme(
        darkTheme = darkTheme,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        content = content
    )
}
