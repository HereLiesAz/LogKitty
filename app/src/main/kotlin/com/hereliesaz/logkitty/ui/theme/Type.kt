package com.hereliesaz.logkitty.ui.theme

import android.util.Log
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.R

// --- Google Fonts Configuration ---
// Defines the provider (GMS Core) and certificates required to fetch fonts at runtime.
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * Enumeration of supported Monospace fonts for code rendering.
 * These map to Google Fonts available via the provider.
 */
enum class CodingFont(val fontName: String, val displayNameRes: Int) {
    SYSTEM("System", R.string.font_system),
    GOOGLE_SANS_FLEX("Google Sans Flex", R.string.font_google_sans_flex),
    ROBOTO_MONO("Roboto Mono", R.string.font_roboto_mono),
    SOURCE_CODE_PRO("Source Code Pro", R.string.font_source_code_pro),
    JETBRAINS_MONO("JetBrains Mono", R.string.font_jetbrains_mono),
    FIRA_CODE("Fira Code", R.string.font_fira_code),
    INCONSOLATA("Inconsolata", R.string.font_inconsolata),
    SPACE_MONO("Space Mono", R.string.font_space_mono),
    UBUNTU_MONO("Ubuntu Mono", R.string.font_ubuntu_mono)
}

/**
 * Returns a [FontFamily] that loads the specified Google Font.
 * Falls back to System Monospace if "System" is selected or if loading fails.
 */
fun getGoogleFontFamily(fontName: String): FontFamily {
    return when (fontName) {
        "System" -> FontFamily.Monospace
        "Google Sans Flex" -> {
            // Google Sans Flex is a local variable font. If it fails to load on some API levels
            // (e.g. 29-30) due to native Font.Builder issues, we fallback to SansSerif.
            // We use a try-catch here as a safety rail, but we also default the app's
            // main Typography to SansSerif to prevent startup crashes.
            try {
                FontFamily(
                    androidx.compose.ui.text.font.Font(
                        resId = R.font.google_sans_flex,
                        weight = FontWeight.Normal
                    )
                )
            } catch (e: Exception) {
                Log.e("Type", "Failed to load Google Sans Flex, falling back to SansSerif", e)
                FontFamily.SansSerif
            }
        }
        else -> FontFamily(
            Font(googleFont = GoogleFont(fontName), fontProvider = provider)
        )
    }
}

// Default Typography styles.
// Note: The LogBottomSheet uses explicit font sizes from Settings, bypassing some of this.
// We default this to SansSerif to prevent startup crashes if the local google_sans_flex.ttf
// is corrupted or incompatible with the current Android version's native font builder.
val GoogleSansFlex = FontFamily.SansSerif

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
