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
    val fontSize: Int, // Added (SP)
    val fontFamily: String, // Added
    val isRootEnabled: Boolean,
    val isLogReversed: Boolean,
    val prohibitedTags: List<String>,
    val logColors: Map<String, Int>
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

    private val _prohibitedTags = MutableStateFlow(loadProhibitedTags())
    val prohibitedTags: StateFlow<Set<String>> = _prohibitedTags.asStateFlow()

    private val _logColors = MutableStateFlow(loadLogColors())
    val logColors: StateFlow<Map<LogLevel, Color>> = _logColors.asStateFlow()

    // ... (Keep existing Setters) ...

    fun setFontSize(size: Int) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply()
        _fontSize.value = size
    }

    fun setFontFamily(fontEnumName: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, fontEnumName).apply()
        _fontFamily.value = fontEnumName
    }

    // ... (Keep existing Color/Tag methods) ...

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
            logColors = colorMap
        )
        return try {
            Json.encodeToString(exported)
        } catch (e: Exception) { "{}" }
    }

    fun importPreferences(jsonString: String) {
        try {
            val imported = Json.decodeFromString<ExportedPreferences>(jsonString)
            // ... (Import other fields)
            setFontSize(imported.fontSize)
            setFontFamily(imported.fontFamily)
            // ... (Import remaining fields)
        } catch (e: Exception) { e.printStackTrace() }
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
    }
}
