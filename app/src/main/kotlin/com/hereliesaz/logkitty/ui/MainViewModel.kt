package com.hereliesaz.logkitty.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.logkitty.services.LogKittyAccessibilityService
import com.hereliesaz.logkitty.ui.delegates.StateDelegate
import com.hereliesaz.logkitty.utils.LogcatReader
import com.hereliesaz.logkitty.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // Derived state for filtered logs
    val filteredSystemLog = combine(
        stateDelegate.systemLog,
        _currentForegroundApp,
        isContextModeEnabled,
        customFilter
    ) { logs, app, enabled, filter ->
        var result = logs
        if (enabled && !app.isNullOrBlank()) {
            result = result.filter { it.contains(app, ignoreCase = true) }
        }
        if (filter.isNotBlank()) {
            result = result.filter { it.contains(filter, ignoreCase = true) }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED) {
                val pkg = intent.getStringExtra("PACKAGE_NAME")
                _currentForegroundApp.value = pkg
            }
        }
    }

    init {
        viewModelScope.launch {
            LogcatReader.observe().collect {
                stateDelegate.appendSystemLog(it)
            }
        }

        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_FOREGROUND_APP_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(receiver, filter)
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

    /** Clears the log. */
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

    /** Sends a prompt (stub for now). */
    fun sendPrompt(p: String?) {
        // No-op or TODO: Implement simple chat logging if needed
    }
}
