package com.hereliesaz.logkitty.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.logkitty.services.LogKittyAccessibilityService
import com.hereliesaz.logkitty.ui.delegates.StateDelegate
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.utils.LogcatReader
import com.hereliesaz.logkitty.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/**
 * Represents a "Tab" in the UI.
 * Can be a system tab (all logs), an error tab (only errors), or an app-specific tab.
 */
data class LogTab(
    val id: String,
    val title: String,
    val type: TabType,
    val filterValue: String? = null
)

enum class TabType {
    SYSTEM,
    ERRORS,
    APP
}

/**
 * [MainViewModel] is the brain of the application.
 *
 * **Role:**
 * It acts as the bridge between the Data Layer ([LogcatReader], [UserPreferences]) and the UI Layer
 * ([LogBottomSheet], [SettingsScreen]).
 *
 * **Key Features:**
 * 1. **Singleton-like Scope:** It is instantiated manually in `MainApplication` to ensure that
 *    both the Overlay Service and the Settings Activity share the exact same instance and data.
 * 2. **Reactive Data Pipeline:** It combines raw logs, user filters, prohibited tags, and tab selection
 *    into a single `filteredSystemLog` flow that the UI simply observes.
 * 3. **Context Awareness:** It listens to the accessibility service to automatically create tabs for
 *    foreground applications.
 */
class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    // Repositories
    private val userPreferences = UserPreferences(application)

    // Delegate to handle the heavy lifting of log buffering.
    val stateDelegate = StateDelegate(viewModelScope)

    // --- State Flows ---

    // The package name of the app currently on screen (detected by Accessibility Service).
    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp

    // Preference Flows (Directly exposed from UserPreferences)
    val isContextModeEnabled: StateFlow<Boolean> = userPreferences.isContextModeEnabled
    val customFilter: StateFlow<String> = userPreferences.customFilter
    val overlayOpacity: StateFlow<Float> = userPreferences.overlayOpacity
    val backgroundColor: StateFlow<Int> = userPreferences.backgroundColor
    val fontSize: StateFlow<Int> = userPreferences.fontSize
    val fontFamily: StateFlow<String> = userPreferences.fontFamily
    val isRootEnabled: StateFlow<Boolean> = userPreferences.isRootEnabled
    val isLogReversed: StateFlow<Boolean> = userPreferences.isLogReversed
    val prohibitedTags: StateFlow<Set<String>> = userPreferences.prohibitedTags
    val logColors: StateFlow<Map<LogLevel, Color>> = userPreferences.logColors
    val showTimestamp: StateFlow<Boolean> = userPreferences.showTimestamp
    val bufferSize: StateFlow<Int> = userPreferences.bufferSize
    val activeLogLevels: StateFlow<Set<String>> = userPreferences.activeLogLevels

    // Tab Management
    private val systemTab = LogTab("system", "System", TabType.SYSTEM)
    private val errorsTab = LogTab("errors", "Errors", TabType.ERRORS)

    private val _tabs = MutableStateFlow(listOf(systemTab, errorsTab))
    val tabs: StateFlow<List<LogTab>> = _tabs

    private val _selectedTab = MutableStateFlow(systemTab)
    val selectedTab: StateFlow<LogTab> = _selectedTab

    // --- The Core Pipeline ---
    // This Flow combines all inputs to produce the final list of logs shown on screen.
    // It re-runs whenever any of the inputs (logs, tab, filter, etc.) change.
    val filteredSystemLog = combine(
        stateDelegate.systemLog,
        _selectedTab,
        customFilter,
        prohibitedTags,
        activeLogLevels
    ) { logs, tab, userFilter, prohibited, levels ->
        var result = logs

        // 1. Filter Log Levels (Verbose, Debug, Info, etc.)
        // Optimization: Only run if some levels are actually disabled.
        if (levels.size < LogLevel.values().size) {
            result = result.filter { line ->
                val level = LogLevel.fromLine(line)
                levels.contains(level.name)
            }
        }

        // 2. Filter prohibited tags (Blacklist)
        if (prohibited.isNotEmpty()) {
            result = result.filter { logLine ->
                // Check if any prohibited tag exists in the line.
                // Case-insensitive for better UX.
                prohibited.none { tag -> logLine.contains(tag, ignoreCase = true) }
            }
        }

        // 3. Tab-based filtering
        when (tab.type) {
            TabType.SYSTEM -> { /* Show all (subject to other filters) */ }
            TabType.ERRORS -> {
                // Heuristic for finding error logs.
                result = result.filter { it.contains(" E/") || it.contains(" E ") }
            }
            TabType.APP -> {
                // Show logs containing the package name.
                val pkg = tab.filterValue
                if (!pkg.isNullOrBlank()) {
                    result = result.filter { it.contains(pkg, ignoreCase = true) }
                }
            }
        }

        // 4. User custom text filter (Search bar)
        if (userFilter.isNotBlank()) {
            result = result.filter { it.contains(userFilter, ignoreCase = true) }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Accessibility Receiver ---
    // Listens for broadcasts from LogKittyAccessibilityService.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED) {
                val pkg = intent.getStringExtra("PACKAGE_NAME")
                _currentForegroundApp.value = pkg

                // Automatically add a tab for the current app if desired.
                if (!pkg.isNullOrBlank()) {
                    addAppTab(pkg)
                }
            }
        }
    }

    private var logJob: Job? = null

    init {
        // Observe Root Mode toggle.
        // If changed, we must restart the LogcatReader with the new privileges.
        viewModelScope.launch {
            isRootEnabled.collect { useRoot ->
                logJob?.cancel() // Stop existing reader
                stateDelegate.clearLog() // Clear old logs (optional, but cleaner)
                logJob = launch {
                    LogcatReader.observe(useRoot).collect {
                        stateDelegate.appendSystemLog(it)
                    }
                }
            }
        }

        // Register the Accessibility Receiver
        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
    }

    /**
     * Adds a temporary tab for a specific application package.
     */
    private fun addAppTab(pkg: String) {
        _tabs.update { currentTabs ->
            if (currentTabs.any { it.filterValue == pkg }) {
                currentTabs // Tab already exists
            } else {
                currentTabs + LogTab("app_$pkg", pkg, TabType.APP, pkg)
            }
        }
    }

    fun selectTab(tab: LogTab) {
        _selectedTab.value = tab
    }

    fun closeTab(tab: LogTab) {
        if (tab.type == TabType.APP) {
            _tabs.update { it - tab }
            // If we closed the active tab, switch back to System.
            if (_selectedTab.value == tab) {
                _selectedTab.value = _tabs.value.firstOrNull() ?: systemTab
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Actions Delegate to Data Layer ---

    fun clearLog() = stateDelegate.clearLog()

    fun toggleContextMode() {
        userPreferences.setContextModeEnabled(!isContextModeEnabled.value)
    }

    fun setCustomFilter(filter: String) {
        userPreferences.setCustomFilter(filter)
    }

    fun setOverlayOpacity(opacity: Float) {
        userPreferences.setOverlayOpacity(opacity)
    }

    fun setBackgroundColor(color: Int) {
        userPreferences.setBackgroundColor(color)
    }

    fun setFontSize(size: Int) {
        userPreferences.setFontSize(size)
    }

    fun setFontFamily(font: CodingFont) {
        userPreferences.setFontFamily(font.name)
    }

    fun setRootEnabled(enabled: Boolean) {
        userPreferences.setRootEnabled(enabled)
    }

    fun setLogReversed(enabled: Boolean) {
        userPreferences.setLogReversed(enabled)
    }
    
    fun setShowTimestamp(enabled: Boolean) {
        userPreferences.setShowTimestamp(enabled)
    }
    
    fun setBufferSize(size: Int) {
        userPreferences.setBufferSize(size)
    }
    
    fun toggleLogLevel(level: LogLevel, enabled: Boolean) {
        userPreferences.toggleLogLevel(level.name, enabled)
    }

    fun setLogColor(level: LogLevel, color: Color) {
        userPreferences.setLogColor(level, color)
    }

    fun resetLogColors() {
        userPreferences.resetLogColors()
    }

    /**
     * Intelligent tag extraction for the "Prohibit" feature.
     * Tries to parse the tag from the log line using Regex.
     */
    fun prohibitLog(logLine: String) {
        // Standard Android Logcat format: "MM-DD HH:MM:SS.mmm PID TID L Tag: Message"
        // We look for " L Tag:" where L is a single letter log level.
        val tagRegex = Regex("""\s([A-Z])\/(.*?):""")
        val match = tagRegex.find(logLine)
        
        val tag = if (match != null) {
            match.groupValues.getOrNull(2)?.trim()
        } else {
            // Fallback: Just take the start of the line up to the first colon.
            logLine.split(":").firstOrNull()?.takeLast(20)
        }

        if (!tag.isNullOrBlank()) {
            userPreferences.addProhibitedTag(tag)
        }
    }

    fun removeProhibitedTag(tag: String) {
        userPreferences.removeProhibitedTag(tag)
    }

    fun exportPreferences() = userPreferences.exportPreferences()
    fun importPreferences(json: String) = userPreferences.importPreferences(json)

    // Placeholder for future AI prompt features.
    fun sendPrompt(p: String?) { }
}
