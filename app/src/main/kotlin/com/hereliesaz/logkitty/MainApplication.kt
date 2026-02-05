package com.hereliesaz.logkitty

import android.app.Application
import com.hereliesaz.logkitty.ui.MainViewModel
import com.hereliesaz.logkitty.utils.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * [MainApplication] serves as the global entry point for the app process.
 *
 * Its primary responsibilities are:
 * 1. Initializing the global [MainViewModel] singleton. This is crucial because both the
 *    [MainActivity] (UI) and [LogKittyOverlayService] (Overlay) need to share the exact same
 *    instance of the ViewModel to sync state (logs, preferences, filters).
 * 2. Setting up the [CrashReporter] to catch and upload unhandled exceptions.
 */
class MainApplication : Application() {

    // The singleton ViewModel instance shared across the Activity and Service.
    // Kept here to survive Activity recreation and provide access to the Service.
    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Crash Reporting mechanisms.
        // This installs a default UncaughtExceptionHandler.
        val crashReporter = CrashReporter(this)
        crashReporter.init()

        // Attempt to upload any pending crash reports from previous runs in the background.
        CoroutineScope(Dispatchers.IO).launch {
            crashReporter.uploadPendingReports()
        }

        // Initialize the shared ViewModel.
        // We pass 'this' (Application Context) to it.
        mainViewModel = MainViewModel(this)
    }
}
