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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    val stateDelegate = StateDelegate(viewModelScope)

    private val _currentForegroundApp = MutableStateFlow<String?>(null)
    val currentForegroundApp: StateFlow<String?> = _currentForegroundApp

    private val _isContextModeEnabled = MutableStateFlow(false)
    val isContextModeEnabled: StateFlow<Boolean> = _isContextModeEnabled

    // Derived state for filtered logs
    val filteredSystemLog = combine(
        stateDelegate.systemLog,
        _currentForegroundApp,
        _isContextModeEnabled
    ) { logs, app, enabled ->
        if (enabled && !app.isNullOrBlank()) {
            logs.filter { it.contains(app, ignoreCase = true) }
        } else {
            logs
        }
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
        _isContextModeEnabled.value = !_isContextModeEnabled.value
    }

    /** Sends a prompt (stub for now). */
    fun sendPrompt(p: String?) {
        // No-op or TODO: Implement simple chat logging if needed
    }
}
