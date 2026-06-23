package com.hereliesaz.logkitty

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.android.play.core.splitcompat.SplitCompat
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
class MainApplication : Application(), ViewModelStoreOwner {

    // Application-scoped ViewModel store so [mainViewModel] is hosted by a real ViewModelProvider
    // (proper viewModelScope, single shared instance) instead of being constructed by hand.
    override val viewModelStore: ViewModelStore = ViewModelStore()

    // The singleton ViewModel instance shared across the Activity and Service.
    // Kept here to survive Activity recreation and provide access to the Service.
    lateinit var mainViewModel: MainViewModel
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Make code/resources from on-demand feature splits available to this process immediately
        // after install, without requiring an app restart, so reflectively-loaded feature classes
        // (FeatureLoader) resolve right away.
        SplitCompat.install(this)
    }

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

        // Initialize the shared ViewModel via a ViewModelProvider backed by this app-scoped store,
        // so it gets a properly-managed lifecycle/viewModelScope rather than being newed up directly.
        mainViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[MainViewModel::class.java]

        // The AdMob SDK is initialized by the :feature:ads module when its banner is shown.
    }
}
