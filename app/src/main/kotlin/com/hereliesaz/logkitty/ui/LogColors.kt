package com.hereliesaz.logkitty.ui

import androidx.compose.ui.graphics.Color

/**
 * [LogLevel] mirrors the canonical Android Logcat priorities.
 *
 * The default colors come from the "Material" scheme. The active scheme is resolved at runtime
 * by [LogColorScheme] which can return either a per-level palette or a tag-aware palette.
 */
enum class LogLevel(val letter: String, val defaultColor: Color) {
    VERBOSE("V", Color(0xFFBDBDBD)),
    DEBUG("D", Color(0xFF2196F3)),
    INFO("I", Color(0xFF4CAF50)),
    WARNING("W", Color(0xFFFF9800)),
    ERROR("E", Color(0xFFF44336)),
    ASSERT("A", Color(0xFF9C27B0));

    companion object {
        private val tagWithLetterRegex = Regex("""\s([VDIWEA])/([^\s:]+)""")
        private val spacedLevelRegex = Regex("""\s([VDIWEA])\s""")

        private val letterToLevel = mapOf(
            "V" to VERBOSE, "D" to DEBUG, "I" to INFO,
            "W" to WARNING, "E" to ERROR, "A" to ASSERT,
        )

        fun fromLine(line: String): LogLevel {
            tagWithLetterRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                return letterToLevel[it] ?: VERBOSE
            }
            spacedLevelRegex.find(line)?.groupValues?.getOrNull(1)?.let {
                return letterToLevel[it] ?: VERBOSE
            }
            return VERBOSE
        }

        fun tagFromLine(line: String): String? {
            val match = tagWithLetterRegex.find(line) ?: return null
            return match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * Built-in color schemes for log entries.
 *
 * - [MATERIAL]: Modern Material Design palette (default).
 * - [AOSP]: Mirrors the colors used by Android Studio's Logcat tool window.
 * - [PIDCAT]: Inspired by Jake Wharton's pidcat — high-contrast terminal tones.
 * - [MONOCHROME]: Single color per level scaled by intensity, ideal for low-light viewing.
 * - [SOLARIZED]: Solarized Dark palette for prolonged sessions.
 * - [CUSTOM]: User-defined per-level colors stored via [com.hereliesaz.logkitty.utils.UserPreferences].
 */
enum class LogColorScheme(val displayName: String, val palette: Map<LogLevel, Color>) {
    MATERIAL("Material", mapOf(
        LogLevel.VERBOSE to Color(0xFFBDBDBD),
        LogLevel.DEBUG to Color(0xFF2196F3),
        LogLevel.INFO to Color(0xFF4CAF50),
        LogLevel.WARNING to Color(0xFFFF9800),
        LogLevel.ERROR to Color(0xFFF44336),
        LogLevel.ASSERT to Color(0xFF9C27B0),
    )),
    AOSP("Android Studio", mapOf(
        LogLevel.VERBOSE to Color(0xFFBBBBBB),
        LogLevel.DEBUG to Color(0xFF6897BB),
        LogLevel.INFO to Color(0xFF6A8759),
        LogLevel.WARNING to Color(0xFFBBB529),
        LogLevel.ERROR to Color(0xFFFF6B68),
        LogLevel.ASSERT to Color(0xFFCC7832),
    )),
    PIDCAT("Pidcat", mapOf(
        LogLevel.VERBOSE to Color(0xFFAAAAAA),
        LogLevel.DEBUG to Color(0xFF00FFFF),
        LogLevel.INFO to Color(0xFF00FF00),
        LogLevel.WARNING to Color(0xFFFFFF00),
        LogLevel.ERROR to Color(0xFFFF0033),
        LogLevel.ASSERT to Color(0xFFFF66FF),
    )),
    MONOCHROME("Monochrome", mapOf(
        LogLevel.VERBOSE to Color(0xFF666666),
        LogLevel.DEBUG to Color(0xFF888888),
        LogLevel.INFO to Color(0xFFAAAAAA),
        LogLevel.WARNING to Color(0xFFCCCCCC),
        LogLevel.ERROR to Color(0xFFFFFFFF),
        LogLevel.ASSERT to Color(0xFFFFFFFF),
    )),
    SOLARIZED("Solarized Dark", mapOf(
        LogLevel.VERBOSE to Color(0xFF93A1A1),
        LogLevel.DEBUG to Color(0xFF268BD2),
        LogLevel.INFO to Color(0xFF859900),
        LogLevel.WARNING to Color(0xFFB58900),
        LogLevel.ERROR to Color(0xFFDC322F),
        LogLevel.ASSERT to Color(0xFFD33682),
    )),
    CUSTOM("Custom", MATERIAL.palette);

    fun colorFor(level: LogLevel): Color = palette[level] ?: Color.White
}

/**
 * Tag-based color overrides applied on top of the level color.
 *
 * Returns a color for the *tag portion* of a log line when the tag matches one of the
 * common Android system components. This delivers richer visual cues without forcing the user
 * to manage per-tag mappings manually.
 */
object TagColors {
    private val rules: List<Pair<Regex, Color>> = listOf(
        Regex("^ActivityManager$") to Color(0xFF80DEEA),
        Regex("^ActivityTaskManager$") to Color(0xFF80DEEA),
        Regex("^PackageManager$") to Color(0xFFA5D6A7),
        Regex("^WindowManager$") to Color(0xFFCE93D8),
        Regex("^InputMethodManager$") to Color(0xFFFFE082),
        Regex("^ConnectivityService$") to Color(0xFF90CAF9),
        Regex("^WifiService$") to Color(0xFF90CAF9),
        Regex("^Bluetooth.*") to Color(0xFF82B1FF),
        Regex("^Choreographer$") to Color(0xFFFFAB91),
        Regex("^SurfaceFlinger$") to Color(0xFFB39DDB),
        Regex("^OpenGLRenderer$") to Color(0xFFB39DDB),
        Regex("^GraphicsEnvironment$") to Color(0xFFB39DDB),
        Regex("^System\\.err$") to Color(0xFFEF9A9A),
        Regex("^AndroidRuntime$") to Color(0xFFEF9A9A),
        Regex("^DEBUG$") to Color(0xFFEF9A9A),
        Regex("^libc(\\-?[a-z]*)?$") to Color(0xFFEF9A9A),
        Regex("^GooglePlayServices.*") to Color(0xFFFFD54F),
        Regex("^Firebase.*") to Color(0xFFFFD54F),
        Regex("^Glide$") to Color(0xFFFFCC80),
        Regex("^OkHttp$") to Color(0xFFFFCC80),
        Regex("^chromium$", RegexOption.IGNORE_CASE) to Color(0xFFAED581),
        Regex("^StrictMode$") to Color(0xFFFFB74D),
        Regex("^GC.*") to Color(0xFFA1887F),
        Regex("^art$") to Color(0xFFA1887F),
        Regex("^zygote.*") to Color(0xFFA1887F),
        Regex("^Compose.*") to Color(0xFF80CBC4),
    )

    fun colorFor(tag: String?): Color? {
        if (tag.isNullOrBlank()) return null
        return rules.firstOrNull { it.first.containsMatchIn(tag) }?.second
    }
}

/**
 * Data class representing the full color scheme for logs.
 * Maintained for backward compatibility with code that referenced this older shape.
 */
data class ColorScheme(
    val verbose: Color = LogLevel.VERBOSE.defaultColor,
    val debug: Color = LogLevel.DEBUG.defaultColor,
    val info: Color = LogLevel.INFO.defaultColor,
    val warning: Color = LogLevel.WARNING.defaultColor,
    val error: Color = LogLevel.ERROR.defaultColor,
    val assert: Color = LogLevel.ASSERT.defaultColor
)
