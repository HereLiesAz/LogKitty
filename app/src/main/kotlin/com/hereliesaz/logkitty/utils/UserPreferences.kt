package com.hereliesaz.logkitty.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.hereliesaz.logkitty.ui.LogLevel
import com.hereliesaz.logkitty.ui.theme.CodingFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExportedPreferences(
    val contextMode: Boolean,
    val customFilter: String,
    val overlayOpacity: Float,
    val backgroundColor: Int,
    val fontSize: Int,
    val fontFamily: String,
    val isRootEnabled: Boolean,
    val isLogReversed: Boolean,
    val prohibitedTags: List<String>,
    val logColors: Map<String, Int>,
    val showTimestamp: Boolean, // Restored
    val bufferSize: Int,        // Restored
    val activeLogLevels: Set<String> // Restored
)

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isContextModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CONTEXT_MODE, false))
    val isContextModeEnabled: StateFlow<Boolean> = _isContextModeEnabled.asStateFlow()

    private val _customFilter = MutableStateFlow(prefs.getString(KEY_CUSTOM_FILTER, "") ?: "")
    val customFilter: StateFlow<String> = _customFilter.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_OPACITY, 0.9f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _backgroundColor = MutableStateFlow(prefs.getInt(KEY_BACKGROUND_COLOR, android.graphics.Color.BLACK))
    val backgroundColor: StateFlow<Int> = _backgroundColor.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getInt(KEY_FONT_SIZE, 12))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _fontFamily = MutableStateFlow(prefs.getString(KEY_FONT_FAMILY, CodingFont.SYSTEM.name) ?: CodingFont.SYSTEM.name)
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(prefs.getBoolean(KEY_IS_ROOT_ENABLED, false))
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

    private val _isLogReversed = MutableStateFlow(prefs.getBoolean(KEY_IS_LOG_REVERSED, false))
    val isLogReversed: StateFlow<Boolean> = _isLogReversed.asStateFlow()

    // --- RESTORED PREFERENCES ---
    private val _showTimestamp = MutableStateFlow(prefs.getBoolean(KEY_SHOW_TIMESTAMP, true))
    val showTimestamp: StateFlow<Boolean> = _showTimestamp.asStateFlow()

    private val _bufferSize = MutableStateFlow(prefs.getInt(KEY_BUFFER_SIZE, 1000))
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    private val _activeLogLevels = MutableStateFlow(
        prefs.getStringSet(KEY_ACTIVE_LOG_LEVELS, LogLevel.values().map { it.name }.toSet()) 
            ?: LogLevel.values().map { it.name }.toSet()
    )
    val activeLogLevels: StateFlow<Set<String>> = _activeLogLevels.asStateFlow()
    // ---------------------------

    private val _prohibitedTags = MutableStateFlow(loadProhibitedTags())
    val prohibitedTags: StateFlow<Set<String>> = _prohibitedTags.asStateFlow()

    private val _logColors = MutableStateFlow(loadLogColors())
    val logColors: StateFlow<Map<LogLevel, Color>> = _logColors.asStateFlow()

    fun setContextModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTEXT_MODE, enabled).apply()
        _isContextModeEnabled.value = enabled
    }

    fun setRootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ROOT_ENABLED, enabled).apply()
        _isRootEnabled.value = enabled
    }

    fun setLogReversed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOG_REVERSED, enabled).apply()
        _isLogReversed.value = enabled
    }

    fun setShowTimestamp(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_TIMESTAMP, enabled).apply()
        _showTimestamp.value = enabled
    }

    fun setBufferSize(size: Int) {
        prefs.edit().putInt(KEY_BUFFER_SIZE, size).apply()
        _bufferSize.value = size
    }

    fun toggleLogLevel(levelName: String, enabled: Boolean) {
        val current = _activeLogLevels.value.toMutableSet()
        if (enabled) current.add(levelName) else current.remove(levelName)
        prefs.edit().putStringSet(KEY_ACTIVE_LOG_LEVELS, current).apply()
        _activeLogLevels.value = current
    }

    fun setCustomFilter(filter: String) {
        prefs.edit().putString(KEY_CUSTOM_FILTER, filter).apply()
        _customFilter.value = filter
    }

    fun setOverlayOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_OPACITY, opacity).apply()
        _overlayOpacity.value = opacity
    }

    fun setBackgroundColor(color: Int) {
        prefs.edit().putInt(KEY_BACKGROUND_COLOR, color).apply()
        _backgroundColor.value = color
    }

    fun setFontSize(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply()
        _fontSize.value = size
    }

    fun setFontFamily(fontEnumName: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, fontEnumName).apply()
        _fontFamily.value = fontEnumName
    }

    fun addProhibitedTag(tag: String) {
        val current = _prohibitedTags.value.toMutableSet()
        current.add(tag)
        _prohibitedTags.value = current
        saveProhibitedTags(current)
    }

    fun removeProhibitedTag(tag: String) {
        val current = _prohibitedTags.value.toMutableSet()
        current.remove(tag)
        _prohibitedTags.value = current
        saveProhibitedTags(current)
    }

    fun setLogColor(level: LogLevel, color: Color) {
        val current = _logColors.value.toMutableMap()
        current[level] = color
        _logColors.value = current
        prefs.edit().putInt(getKeyForColor(level), color.toArgb()).apply()
    }

    fun resetLogColors() {
        val editor = prefs.edit()
        val defaultColors = mutableMapOf<LogLevel, Color>()
        LogLevel.values().forEach { level ->
            editor.remove(getKeyForColor(level))
            defaultColors[level] = level.defaultColor
        }
        editor.apply()
        _logColors.value = defaultColors
    }

    private fun loadProhibitedTags(): Set<String> {
        return prefs.getStringSet(KEY_PROHIBITED_TAGS, emptySet()) ?: emptySet()
    }

    private fun saveProhibitedTags(tags: Set<String>) {
        prefs.edit().putStringSet(KEY_PROHIBITED_TAGS, tags).apply()
    }

    private fun loadLogColors(): Map<LogLevel, Color> {
        val colors = mutableMapOf<LogLevel, Color>()
        LogLevel.values().forEach { level ->
            val colorInt = prefs.getInt(getKeyForColor(level), level.defaultColor.toArgb())
            colors[level] = Color(colorInt)
        }
        return colors
    }

    private fun getKeyForColor(level: LogLevel) = "color_${level.name}"

    fun exportPreferences(): String {
        val colorMap = _logColors.value.mapKeys { it.key.name }.mapValues { it.value.toArgb() }
        val exported = ExportedPreferences(
            contextMode = _isContextModeEnabled.value,
            customFilter = _customFilter.value,
            overlayOpacity = _overlayOpacity.value,
            backgroundColor = _backgroundColor.value,
            fontSize = _fontSize.value,
            fontFamily = _fontFamily.value,
            isRootEnabled = _isRootEnabled.value,
            isLogReversed = _isLogReversed.value,
            prohibitedTags = _prohibitedTags.value.toList(),
            logColors = colorMap,
            showTimestamp = _showTimestamp.value,
            bufferSize = _bufferSize.value,
            activeLogLevels = _activeLogLevels.value
        )
        return try {
            Json.encodeToString(exported)
        } catch (e: Exception) { "{}" }
    }

    fun importPreferences(jsonString: String) {
        try {
            val imported = Json.decodeFromString<ExportedPreferences>(jsonString)
            setContextModeEnabled(imported.contextMode)
            setCustomFilter(imported.customFilter)
            setOverlayOpacity(imported.overlayOpacity)
            setBackgroundColor(imported.backgroundColor)
            setFontSize(imported.fontSize)
            setFontFamily(imported.fontFamily)
            setRootEnabled(imported.isRootEnabled)
            setLogReversed(imported.isLogReversed)
            setShowTimestamp(imported.showTimestamp)
            setBufferSize(imported.bufferSize)
            
            val levels = imported.activeLogLevels.toMutableSet()
            prefs.edit().putStringSet(KEY_ACTIVE_LOG_LEVELS, levels).apply()
            _activeLogLevels.value = levels

            val tags = imported.prohibitedTags.toSet()
            _prohibitedTags.value = tags
            saveProhibitedTags(tags)

            val editor = prefs.edit()
            val newColors = mutableMapOf<LogLevel, Color>()
            LogLevel.values().forEach { newColors[it] = it.defaultColor }

            imported.logColors.forEach { (levelName, colorInt) ->
                try {
                    val level = LogLevel.valueOf(levelName)
                    val color = Color(colorInt)
                    newColors[level] = color
                    editor.putInt(getKeyForColor(level), colorInt)
                } catch (e: IllegalArgumentException) { }
            }
            editor.apply()
            _logColors.value = newColors

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PREFS_NAME = "logkitty_user_prefs"
        private const val KEY_CONTEXT_MODE = "context_mode"
        private const val KEY_CUSTOM_FILTER = "custom_filter"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_IS_ROOT_ENABLED = "is_root_enabled"
        private const val KEY_IS_LOG_REVERSED = "is_log_reversed"
        private const val KEY_PROHIBITED_TAGS = "prohibited_tags"
        private const val KEY_SHOW_TIMESTAMP = "show_timestamp"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_ACTIVE_LOG_LEVELS = "active_log_levels"
    }
}
