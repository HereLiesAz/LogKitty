package com.hereliesaz.logkitty.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.hereliesaz.logkitty.ui.LogLevel
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
    val isRootEnabled: Boolean,
    val prohibitedTags: List<String>,
    val logColors: Map<String, Int> // Store colors as ARGB Ints, keyed by LogLevel.name
)

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isContextModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_CONTEXT_MODE, false))
    val isContextModeEnabled: StateFlow<Boolean> = _isContextModeEnabled.asStateFlow()

    private val _customFilter = MutableStateFlow(prefs.getString(KEY_CUSTOM_FILTER, "") ?: "")
    val customFilter: StateFlow<String> = _customFilter.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_OPACITY, 1.0f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _isRootEnabled = MutableStateFlow(prefs.getBoolean(KEY_IS_ROOT_ENABLED, false))
    val isRootEnabled: StateFlow<Boolean> = _isRootEnabled.asStateFlow()

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

    fun setCustomFilter(filter: String) {
        prefs.edit().putString(KEY_CUSTOM_FILTER, filter).apply()
        _customFilter.value = filter
    }

    fun setOverlayOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_OPACITY, opacity).apply()
        _overlayOpacity.value = opacity
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

    // Export/Import functionality
    fun exportPreferences(): String {
        val colorMap = _logColors.value.mapKeys { it.key.name }.mapValues { it.value.toArgb() }
        val exported = ExportedPreferences(
            contextMode = _isContextModeEnabled.value,
            customFilter = _customFilter.value,
            overlayOpacity = _overlayOpacity.value,
            isRootEnabled = _isRootEnabled.value,
            prohibitedTags = _prohibitedTags.value.toList(),
            logColors = colorMap
        )
        return try {
            Json.encodeToString(exported)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun importPreferences(jsonString: String) {
        try {
            val imported = Json.decodeFromString<ExportedPreferences>(jsonString)
            setContextModeEnabled(imported.contextMode)
            setCustomFilter(imported.customFilter)
            setOverlayOpacity(imported.overlayOpacity)
            setRootEnabled(imported.isRootEnabled)

            val tags = imported.prohibitedTags.toSet()
            _prohibitedTags.value = tags
            saveProhibitedTags(tags)

            // Import Colors
            val editor = prefs.edit()
            val newColors = mutableMapOf<LogLevel, Color>()
            // Start with defaults to ensure we have all keys even if JSON is partial
            LogLevel.values().forEach { newColors[it] = it.defaultColor }

            imported.logColors.forEach { (levelName, colorInt) ->
                try {
                    val level = LogLevel.valueOf(levelName)
                    val color = Color(colorInt)
                    newColors[level] = color
                    editor.putInt(getKeyForColor(level), colorInt)
                } catch (e: IllegalArgumentException) {
                    // Ignore invalid level names
                }
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
        private const val KEY_IS_ROOT_ENABLED = "is_root_enabled"
        private const val KEY_PROHIBITED_TAGS = "prohibited_tags"
    }
}
