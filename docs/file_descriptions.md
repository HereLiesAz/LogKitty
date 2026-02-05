# File Descriptions

## Root
*   `AGENTS.md`: Master instructions and index for AI agents.
*   `build.gradle.kts`: Project-level build configuration.
*   `settings.gradle.kts`: Module inclusion settings.
*   `gradle.properties`: Gradle build properties.
*   `version.properties`: Source of truth for project versioning.
*   `get_version.sh`: Script to extract version info.
*   `lint-baseline.xml`: Baseline file for lint warnings.
*   `proguard-rules.pro`: ProGuard/R8 configuration rules.

## app/src/main/kotlin/com/hereliesaz/logkitty/

### Root Package
*   `MainActivity.kt`: The entry point activity. Handles initial setup, permission requests (Overlay, Root), and service starting.
*   `MainApplication.kt`: The Application class. Initializes global singletons like `MainViewModel`.
*   `FileSaverActivity.kt`: A transient Activity used to save log buffers to a file using the System File Picker.

### services/
*   `LogKittyOverlayService.kt`: The core service. Manages the system overlay window, handles window resizing/pass-through logic, and hosts the Compose UI.
*   `LogKittyAccessibilityService.kt`: Background service that detects `TYPE_WINDOW_STATE_CHANGED` events to identify the foreground package for context-aware filtering.

### ui/
*   `LogBottomSheet.kt`: The primary UI composable. Renders the persistent bottom sheet, log list, and control header.
*   `MainViewModel.kt`: The central logic controller. Bridges the `LogcatReader` data, `UserPreferences`, and the UI. Handles filtering and state management.
*   `SettingsScreen.kt`: A dedicated screen for configuring app behavior (opacity, buffer size, prohibited logs, etc.).
*   `ProhibitedLogsScreen.kt`: UI for managing the list of prohibited log tags/strings.
*   `LogColors.kt`: Definitions for log level colors (Verbose, Debug, Info, Warn, Error, Assert).
*   `ColorPickerDialog.kt`: A dialog composable for picking colors (used in settings).

### ui/delegates/
*   `StateDelegate.kt`: The data holder and processor. Manages the circular log buffer and handles the high-frequency log stream batching.

### ui/theme/
*   `Theme.kt`: Jetpack Compose theme definition.
*   `Color.kt`: Color palette definitions.
*   `Type.kt`: Typography definitions.

### utils/
*   `LogcatReader.kt`: The engine that spawns and reads the `logcat` process. Handles stream parsing and resilience.
*   `ComposeLifecycleHelper.kt`: Critical utility for bridging the gap between an Android Service and Jetpack Compose's Lifecycle-aware components.
*   `UserPreferences.kt`: Manages persistence of user settings (DataStore/SharedPreferences) and export/import functionality.
*   `CrashReporter.kt`: A custom `UncaughtExceptionHandler` that captures crashes and attempts to report them (e.g., to GitHub Issues).
