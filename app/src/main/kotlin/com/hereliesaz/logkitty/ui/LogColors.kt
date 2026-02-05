package com.hereliesaz.logkitty.ui

import androidx.compose.ui.graphics.Color

/**
 * [LogLevel] defines the standard Android Logcat levels.
 *
 * It includes a heuristic parsing method [fromLine] to determine the level of a raw log string.
 */
enum class LogLevel(val letter: String, val defaultColor: Color) {
    VERBOSE("V", Color.White),
    DEBUG("D", Color(0xFF2196F3)), // Material Blue 500
    INFO("I", Color(0xFF4CAF50)),  // Material Green 500
    WARNING("W", Color(0xFFFF9800)), // Material Orange 500
    ERROR("E", Color(0xFFF44336)),   // Material Red 500
    ASSERT("A", Color(0xFF9C27B0));  // Material Purple 500

    companion object {
        /**
         * Parses a raw log line to determine its [LogLevel].
         *
         * Since `logcat` output formats vary by device and command flags (e.g. `time` vs `threadtime`),
         * this method uses a heuristic approach looking for standard indicators like " D/" or " E ".
         */
        fun fromLine(line: String): LogLevel {
            // Typical format: "MM-DD HH:MM:SS.mmm PID-TID/Package L/Tag: Message"
            // or "MM-DD HH:MM:SS.mmm PID TID L Tag: Message"
            return when {
                line.contains(" V/") -> VERBOSE
                line.contains(" D/") -> DEBUG
                line.contains(" I/") -> INFO
                line.contains(" W/") -> WARNING
                line.contains(" E/") -> ERROR
                line.contains(" A/") -> ASSERT
                // Fallback for space-delimited formats
                line.contains(" E ") -> ERROR
                line.contains(" W ") -> WARNING
                else -> VERBOSE
            }
        }
    }
}

/**
 * Data class representing the full color scheme for logs.
 * Used for theming and persistence.
 */
data class ColorScheme(
    val verbose: Color = LogLevel.VERBOSE.defaultColor,
    val debug: Color = LogLevel.DEBUG.defaultColor,
    val info: Color = LogLevel.INFO.defaultColor,
    val warning: Color = LogLevel.WARNING.defaultColor,
    val error: Color = LogLevel.ERROR.defaultColor,
    val assert: Color = LogLevel.ASSERT.defaultColor
)
