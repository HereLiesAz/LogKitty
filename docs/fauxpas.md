# Common Faux Pas

*   **Blocking the Main Thread:** `LogcatReader` *must* run on `Dispatchers.IO`. Reading from an InputStream is blocking.
*   **Leaking Views:** `WindowManager.removeView()` must be called in `onDestroy()`.
*   **Ignoring Lifecycle:** Compose views in a Service *need* a `ComposeLifecycleHelper` (LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner) to function correctly. Without this, `LaunchedEffect` and other lifecycle-aware composables will fail or leak.
*   **Hardcoding Package Names:** Avoid hardcoding `com.hereliesaz.ideaz` or `com.hereliesaz.logkitty` in strings if `packageName` context can be used.
