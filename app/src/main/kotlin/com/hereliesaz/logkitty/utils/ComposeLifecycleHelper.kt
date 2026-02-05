package com.hereliesaz.logkitty.utils

import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A critical compatibility helper for running Jetpack Compose in an Android [Service].
 *
 * **The Problem:**
 * Jetpack Compose assumes it is running inside a `ComponentActivity` or `Fragment`, which
 * automatically provide:
 * 1. [LifecycleOwner] (to know when to start/stop composition).
 * 2. [ViewModelStoreOwner] (to scope ViewModels).
 * 3. [SavedStateRegistryOwner] (to save/restore state across config changes).
 *
 * An Android [Service] provides NONE of these. If you try to use `ComposeView` in a Service
 * without setting these owners, the app will crash with "No LifecycleOwner found" errors.
 *
 * **The Solution:**
 * This class implements all those interfaces manually and attaches them to the `ComposeView`'s
 * view tree, tricking Compose into thinking it's running in a valid lifecycle environment.
 */
class ComposeLifecycleHelper(private val view: View) : LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner {

    // Manually manage the Lifecycle registry.
    private val lifecycleRegistry = LifecycleRegistry(this)

    // Manually manage ViewModel store.
    private val store = ViewModelStore()

    // Manually manage SavedStateRegistry.
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // Needed for some Compose components that listen for back presses (even if Service doesn't really handle them).
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        // "Attach" this helper to the view tree.
        // This is what Compose looks for when it calls LocalLifecycleOwner.current
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeOnBackPressedDispatcherOwner(this)
    }

    /**
     * Call this when the Service/View is created.
     * Corresponds to Activity.onCreate().
     */
    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /**
     * Call this when the Service/View becomes visible/active.
     * Corresponds to Activity.onStart().
     */
    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Call this when the Service/View is hidden.
     * Corresponds to Activity.onStop().
     */
    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * Call this when the Service is destroyed.
     * Cleans up the ViewModelStore and disposes of the composition.
     */
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        // Explicitly dispose of the composition to prevent memory leaks in the Service.
        (view as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()
    }
}
