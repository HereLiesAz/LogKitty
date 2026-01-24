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

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)
    val stateDelegate = StateDelegate(viewModelScope)

    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp

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

    private val systemTab = LogTab("system", "System", TabType.SYSTEM)
    private val errorsTab = LogTab("errors", "Errors", TabType.ERRORS)

    private val _tabs = MutableStateFlow(listOf(systemTab, errorsTab))
    val tabs: StateFlow<List<LogTab>> = _tabs

    private val _selectedTab = MutableStateFlow(systemTab)
    val selectedTab: StateFlow<LogTab> = _selectedTab

    val filteredSystemLog = combine(
        stateDelegate.systemLog,
        _selectedTab,
        customFilter,
        prohibitedTags
    ) { logs, tab, userFilter, prohibited ->
        var result = logs

        if (prohibited.isNotEmpty()) {
            result = result.filter { logLine ->
                prohibited.none { tag -> logLine.contains(tag, ignoreCase = true) }
            }
        }

        when (tab.type) {
            TabType.SYSTEM -> { }
            TabType.ERRORS -> {
                result = result.filter { it.contains(" E/") || it.contains(" E ") }
            }
            TabType.APP -> {
                val pkg = tab.filterValue
                if (!pkg.isNullOrBlank()) {
                    result = result.filter { it.contains(pkg, ignoreCase = true) }
                }
            }
        }

        if (userFilter.isNotBlank()) {
            result = result.filter { it.contains(userFilter, ignoreCase = true) }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED) {
                val pkg = intent.getStringExtra("PACKAGE_NAME")
                _currentForegroundApp.value = pkg

                if (!pkg.isNullOrBlank()) {
                    addAppTab(pkg)
                }
            }
        }
    }

    private var logJob: Job? = null

    init {
        viewModelScope.launch {
            isRootEnabled.collect { useRoot ->
                logJob?.cancel()
                stateDelegate.clearLog()
                logJob = launch {
                    LogcatReader.observe(useRoot).collect {
                        stateDelegate.appendSystemLog(it)
                    }
                }
            }
        }

        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
        }
    }

    private fun addAppTab(pkg: String) {
        _tabs.update { currentTabs ->
            if (currentTabs.any { it.filterValue == pkg }) {
                currentTabs
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

    fun setLogColor(level: LogLevel, color: Color) {
        userPreferences.setLogColor(level, color)
    }

    fun resetLogColors() {
        userPreferences.resetLogColors()
    }

    fun prohibitLog(logLine: String) {
        val tagRegex = Regex("""\s([A-Z])\/(.*?):""")
        val match = tagRegex.find(logLine)
        
        val tag = if (match != null) {
            match.groupValues.getOrNull(2)?.trim()
        } else {
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

    fun sendPrompt(p: String?) { }
}
