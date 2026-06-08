package com.hereliesaz.logkitty.ui.theme

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
    return if (fontName == "System") {
        FontFamily.Monospace
    } else {
        FontFamily(
            Font(googleFont = GoogleFont(fontName), fontProvider = provider)
        )
    }
}

// Default Typography styles.
// Note: The LogBottomSheet uses explicit font sizes from Settings, bypassing some of this.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
