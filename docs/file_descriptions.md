# File Descriptions

## Root
*   `AGENTS.md`: Master instructions and index for AI agents.
*   `build.gradle.kts`: Project-level build configuration.
*   `settings.gradle.kts`: Module inclusion settings.
*   `gradle.properties`: Gradle build properties.
*   `version.properties`: Source of truth for project versioning. The `build` field is automatically incremented by `app/build.gradle.kts` during build tasks.
*   `get_version.sh`: Script to extract version info (major.minor.patch.build).
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
*   `LogBottomSheet.kt`: The primary UI composable. Custom 4-detent overlay (HIDDEN/PEEK/HALF/FULL) with tab row, gesture zones, and selectable log items.
*   `SheetController.kt`: Shared state holder for the active detent — consumed by both the Compose UI (for animation) and the hosting Service (for window sizing).
*   `MainViewModel.kt`: The central logic controller. Bridges the `LogcatReader` data, `UserPreferences`, and the UI. Handles per-tab clearing and side-swipe tab navigation.
*   `SettingsScreen.kt`: A dedicated screen for configuring app behavior. Hosts navigation into the prohibited-tags list, the color scheme editor, and preferences export/import.
*   `ProhibitedLogsScreen.kt`: UI for managing the list of prohibited log tags/strings.
*   `ColorSchemeEditorScreen.kt`: Per-level color customization. Selecting any swatch flips the active scheme to CUSTOM.
*   `LogColors.kt`: Log level enum plus the built-in `LogColorScheme` palettes (Material, AOSP, Pidcat, Monochrome, Solarized, Custom) and the tag-based highlight rules.
*   `ColorPickerDialog.kt`: A dialog composable for picking colors (used in settings).

### ui/delegates/
*   `StateDelegate.kt`: The data holder and processor. Tags every log line with a monotonically-increasing `IndexedLogLine.id` (the basis for per-tab clearing), batches incoming lines, and caps the rolling buffer.

### ui/theme/
*   `Theme.kt`: Jetpack Compose theme definition.
*   `Color.kt`: Color palette definitions.
*   `Type.kt`: Typography definitions.

### utils/
*   `LogcatReader.kt`: The engine that spawns and reads the `logcat` process. Handles stream parsing and resilience.
*   `ComposeLifecycleHelper.kt`: Critical utility for bridging the gap between an Android Service and Jetpack Compose's Lifecycle-aware components.
*   `UserPreferences.kt`: Manages persistence of user settings (DataStore/SharedPreferences) and export/import functionality.
*   `CrashReporter.kt`: A custom `UncaughtExceptionHandler` that captures crashes and attempts to report them (e.g., to GitHub Issues).
