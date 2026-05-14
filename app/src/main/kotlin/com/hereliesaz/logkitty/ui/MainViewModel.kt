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
import com.hereliesaz.logkitty.ui.delegates.IndexedLogLine
import com.hereliesaz.logkitty.ui.delegates.StateDelegate
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.utils.LogcatReader
import com.hereliesaz.logkitty.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    val colorScheme: StateFlow<LogColorScheme> = userPreferences.colorScheme
    val tagColoringEnabled: StateFlow<Boolean> = userPreferences.tagColoringEnabled

    /**
     * Per-tab "cleared" baseline. When the user clears a single tab we record the size of the
     * underlying log buffer at that moment and skip everything before it for that tab only.
     */
    private val _tabClearMarks = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Tab Management
    private val systemTab = LogTab("system", "System", TabType.SYSTEM)
    private val errorsTab = LogTab("errors", "Errors", TabType.ERRORS)

    private val _tabs = MutableStateFlow(listOf(systemTab, errorsTab))
    val tabs: StateFlow<List<LogTab>> = _tabs

    private val _selectedTab = MutableStateFlow(systemTab)
    val selectedTab: StateFlow<LogTab> = _selectedTab

    private data class FilterInputs(
        val tab: LogTab,
        val userFilter: String,
        val prohibited: Set<String>,
        val levels: Set<String>,
        val marks: Map<String, Long>
    )

    /**
     * Indexed, filtered view of the log stream for the current tab.
     * Each entry preserves its global id so the UI can stably select / copy / prohibit it.
     */
    val filteredIndexedLog: StateFlow<List<IndexedLogLine>> = run {
        val inputs = combine(_selectedTab, customFilter, prohibitedTags, activeLogLevels, _tabClearMarks) {
            tab, userFilter, prohibited, levels, marks -> FilterInputs(tab, userFilter, prohibited, levels, marks)
        }
        combine(stateDelegate.systemLog, inputs) { logs, input ->
            val clearMark = input.marks[input.tab.id] ?: 0L
            var result: List<IndexedLogLine> = if (clearMark > 0L) logs.filter { it.id > clearMark } else logs

            if (input.levels.size < LogLevel.values().size) {
                result = result.filter { line -> input.levels.contains(LogLevel.fromLine(line.text).name) }
            }
            if (input.prohibited.isNotEmpty()) {
                result = result.filter { line -> input.prohibited.none { tag -> line.text.contains(tag, ignoreCase = true) } }
            }
            when (input.tab.type) {
                TabType.SYSTEM -> { }
                TabType.ERRORS -> result = result.filter {
                    val lvl = LogLevel.fromLine(it.text)
                    lvl == LogLevel.ERROR || lvl == LogLevel.ASSERT
                }
                TabType.APP -> {
                    val pkg = input.tab.filterValue
                    if (!pkg.isNullOrBlank()) result = result.filter { it.text.contains(pkg, ignoreCase = true) }
                }
            }
            if (input.userFilter.isNotBlank()) {
                result = result.filter { it.text.contains(input.userFilter, ignoreCase = true) }
            }
            result
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    /**
     * Backward-compatible textual view retained for callers (e.g. FileSaverActivity).
     */
    val filteredSystemLog: StateFlow<List<String>> = filteredIndexedLog
        .map { list -> list.map { it.text } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    /** Move selection one tab forward / back, wrapping at the ends. */
    fun selectNextTab() = shiftSelection(+1)
    fun selectPreviousTab() = shiftSelection(-1)

    private fun shiftSelection(delta: Int) {
        val list = _tabs.value
        if (list.isEmpty()) return
        val idx = list.indexOf(_selectedTab.value).takeIf { it >= 0 } ?: 0
        val newIdx = ((idx + delta) % list.size + list.size) % list.size
        _selectedTab.value = list[newIdx]
    }

    fun closeTab(tab: LogTab) {
        if (tab.type == TabType.APP) {
            _tabs.update { it - tab }
            if (_selectedTab.value == tab) {
                _selectedTab.value = _tabs.value.firstOrNull() ?: systemTab
            }
            _tabClearMarks.update { it - tab.id }
        }
    }

    /**
     * Clears only the active tab by recording the current max id as that tab's "skip line".
     * Other tabs continue to see historical data.
     */
    fun clearActiveTab() {
        val tabId = _selectedTab.value.id
        _tabClearMarks.update { it + (tabId to stateDelegate.currentMaxId) }
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

    /** Clears the entire shared log buffer (all tabs). */
    fun clearLog() {
        stateDelegate.clearLog()
        _tabClearMarks.value = emptyMap()
    }

    fun setTagColoringEnabled(enabled: Boolean) {
        userPreferences.setTagColoringEnabled(enabled)
    }

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

    fun setColorScheme(scheme: com.hereliesaz.logkitty.ui.LogColorScheme) {
        userPreferences.setColorScheme(scheme)
    }
}
