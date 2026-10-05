# API Reference

This document provides a high-level overview of the key classes and components in the LogKitty codebase.

## Services

### `LogKittyOverlayService`
*   **Package:** `com.hereliesaz.logkitty.services`
*   **Description:** The foreground service responsible for managing the system overlay window.
*   **Key Responsibilities:**
    *   Creating and updating the `WindowManager.LayoutParams`.
    *   Hosting the Jetpack Compose `ComposeView`.
    *   Handling window pass-through interactions (switching between `FLAG_NOT_TOUCHABLE` and interactive modes).
    *   Observing `MainViewModel` and `LogBottomSheet` state to resize the window dynamically.

### `ForegroundAppMonitor`
*   **Package:** `com.hereliesaz.logkitty.services`
*   **Description:** Detects the foreground app for Context Mode, without an accessibility service.
*   **Key Responsibilities:**
    *   Polling `UsageStatsManager` (non-root) or `dumpsys activity activities` (root).
    *   Broadcasting `ACTION_FOREGROUND_APP_CHANGED` when the foreground package changes.

## Core Logic

### `MainViewModel`
*   **Package:** `com.hereliesaz.logkitty.ui`
*   **Description:** The central ViewModel singleton.
*   **Key Responsibilities:**
    *   Orchestrating the data flow between `LogcatReader`, `StateDelegate`, and the UI.
    *   Managing user preferences (opacity, filters, prohibited tags).
    *   Applying regex filtering and keyword searching to the log stream.
    *   Exposing `StateFlow` objects for UI consumption.

### `LogcatReader`
*   **Package:** `com.hereliesaz.logkitty.utils`
*   **Description:** A robust wrapper around the Android `logcat` shell command.
*   **Key Responsibilities:**
    *   Spawning the `logcat` process (standard or root).
    *   Reading `stdout` and `stderr` streams on a background thread.
    *   Parsing raw log lines into structured data (if possible) or passing them as raw text.
    *   Automatically restarting the process if it dies.

### `StateDelegate`
*   **Package:** `com.hereliesaz.logkitty.ui.delegates`
*   **Description:** Efficient state holder for the log buffer.
*   **Key Responsibilities:**
    *   Maintaining a circular buffer of log lines (preventing OOM).
    *   Batching incoming log lines (e.g., every 100ms) to reduce UI recomposition pressure.
    *   Exposing the `systemLog` flow.

## UI Components

### `LogBottomSheet`
*   **Package:** `com.hereliesaz.logkitty.ui`
*   **Description:** The main visual component of the overlay.
*   **Key Responsibilities:**
    *   Rendering the "Peek", "Half", and "Full" states.
    *   Displaying the `LazyColumn` of logs.
    *   Handling user interactions (drag, scroll, copy, clear, settings).

### `SettingsScreen`
*   **Package:** `com.hereliesaz.logkitty.ui`
*   **Description:** The full-screen activity UI for configuring the app.
*   **Key Responsibilities:**
    *   Providing controls for Opacity, Text Size, Buffer Size.
    *   Managing "Prohibited Logs" lists.
    *   Toggling "Root Mode" and "Reverse Log Direction".

## Utilities

### `UserPreferences`
*   **Package:** `com.hereliesaz.logkitty.utils`
*   **Description:** Data persistence layer.
*   **Key Responsibilities:**
    *   Reading/Writing `SharedPreferences` (wrapped in `StateFlow`).
    *   Exporting and Importing preferences as JSON.

### `ComposeLifecycleHelper`
*   **Package:** `com.hereliesaz.logkitty.utils`
*   **Description:** Compatibility utility.
*   **Key Responsibilities:**
    *   Providing a synthetic `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` for Compose views running inside a Service (which lacks these by default).
