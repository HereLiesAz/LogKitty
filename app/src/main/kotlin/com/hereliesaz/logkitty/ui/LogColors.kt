package com.hereliesaz.logkitty.ui

import androidx.compose.ui.graphics.Color

enum class LogLevel(val letter: String, val defaultColor: Color) {
    VERBOSE("V", Color.White),
    DEBUG("D", Color(0xFF2196F3)), // Blue
    INFO("I", Color(0xFF4CAF50)),  // Green
    WARNING("W", Color(0xFFFF9800)), // Orange
    ERROR("E", Color(0xFFF44336)),   // Red
    ASSERT("A", Color(0xFF9C27B0));  // Purple

    companion object {
        fun fromLine(line: String): LogLevel {
            // Typical format: "MM-DD HH:MM:SS.mmm PID-TID/Package L/Tag: Message"
            // or "MM-DD HH:MM:SS.mmm PID TID L Tag: Message"
            // We search for the level indicator.
            // Often standard Logcat Reader output is just the raw string.
            // Heuristic: Check for " V/", " D/", " I/", " W/", " E/", " A/"
            // or just the single char surrounded by spaces if that's how it's formatted.
            // Default `logcat -v time` format: "10-12 11:21:20.123 V/Tag( 123): msg"
            return when {
                line.contains(" V/") -> VERBOSE
                line.contains(" D/") -> DEBUG
                line.contains(" I/") -> INFO
                line.contains(" W/") -> WARNING
                line.contains(" E/") -> ERROR
                line.contains(" A/") -> ASSERT
                // Fallback for some formats
                line.contains(" E ") -> ERROR
                line.contains(" W ") -> WARNING
                else -> VERBOSE
            }
        }
    }
}

data class ColorScheme(
    val verbose: Color = LogLevel.VERBOSE.defaultColor,
    val debug: Color = LogLevel.DEBUG.defaultColor,
    val info: Color = LogLevel.INFO.defaultColor,
    val warning: Color = LogLevel.WARNING.defaultColor,
    val error: Color = LogLevel.ERROR.defaultColor,
    val assert: Color = LogLevel.ASSERT.defaultColor
)
