package com.hereliesaz.logkitty

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.android.play.core.splitcompat.SplitCompat
import com.hereliesaz.logkitty.ui.MainViewModel

/**
 * [MainApplication] serves as the global entry point for the app process.
 *
 * Its primary responsibilities are:
 * 1. Initializing the global [MainViewModel] singleton. This is crucial because both the
 *    [MainActivity] (UI) and [LogKittyOverlayService] (Overlay) need to share the exact same
 *    instance of the ViewModel to sync state (logs, preferences, filters).
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

        // Initialize the shared ViewModel via a ViewModelProvider backed by this app-scoped store,
        // so it gets a properly-managed lifecycle/viewModelScope rather than being newed up directly.
        mainViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[MainViewModel::class.java]

        // Enforce the "auto-delete after N days" / "max total size" settings on saved sessions.
        com.hereliesaz.logkitty.data.LogFileCleanupWorker.schedule(this)
        purgeLegacyGitHubData()
    }

    /**
     * GitHub integration was removed. Wipe anything older versions stored for it: the encrypted PAT
     * store, its Keystore key, and the repo owner/name preferences. Idempotent and cheap.
     */
    private fun purgeLegacyGitHubData() {
        runCatching { deleteSharedPreferences("logkitty_secure_prefs") }
        runCatching {
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias("logkitty_github_pat")) ks.deleteEntry("logkitty_github_pat")
        }
        runCatching {
            getSharedPreferences("logkitty_user_prefs", MODE_PRIVATE).edit()
                .remove("github_owner").remove("github_repo").apply()
        }
        // Pending GitHub run-watch jobs (WorkManager tags each request with its worker class name).
        runCatching {
            androidx.work.WorkManager.getInstance(this)
                .cancelAllWorkByTag("com.hereliesaz.logkitty.work.RunWatchWorker")
        }
    }
}
