package com.hereliesaz.ideaz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.ideaz.ui.delegates.StateDelegate
import com.hereliesaz.ideaz.utils.LogcatReader
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    val stateDelegate = StateDelegate(viewModelScope)

    // --- PUBLIC STATE EXPOSURE ---
    val systemLog = stateDelegate.systemLog

    init {
        viewModelScope.launch {
            LogcatReader.observe().collect {
                stateDelegate.appendSystemLog(it)
            }
        }
    }

    /** Clears the log. */
    fun clearLog() = stateDelegate.clearLog()

    /** Sends a prompt (stub for now). */
    fun sendPrompt(p: String?) {
        // No-op or TODO: Implement simple chat logging if needed
    }
}
