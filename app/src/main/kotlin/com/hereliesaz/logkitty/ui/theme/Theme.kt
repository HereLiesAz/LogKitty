package com.hereliesaz.logkitty.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// --- Dark Theme Palette ---
// High contrast, intended for the overlay primarily.
private val DarkColorScheme = darkColorScheme(
    primary = White,
    secondary = LightGrey,
    tertiary = MediumGrey,
    background = Black,
    surface = DarkGrey,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = White,
    onSurface = White,
)

// --- Light Theme Palette ---
// Standard light mode, mostly used for the Settings screen if the system is light.
private val LightColorScheme = lightColorScheme(
    primary = Black,
    secondary = MediumGrey,
    tertiary = LightGrey,
    background = White,
    surface = OffWhite,
    onPrimary = White,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = Black,
    onSurface = Black,
)

/**
 * [LogKittyTheme] is the centralized Material3 theme wrapper.
 *
 * It automatically adapts to system settings (Dark/Light mode) and supports Dynamic Colors (Material You)
 * on Android 12+ devices, though we default to disabled for the overlay to ensure consistent high contrast.
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to system setting.
 * @param dynamicColor Whether to use wallpaper-derived colors (Android S+). Default is false.
 */
@Composable
fun LogKittyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
