# File Descriptions

## Root
*   `AGENTS.md`: Master instructions and index.
*   `build.gradle.kts`: Project-level build configuration.
*   `settings.gradle.kts`: Module inclusion.
*   `gradle.properties`: Build properties.
*   `version.properties`: Versioning source of truth.

## app/src/main/kotlin/com/hereliesaz/logkitty/

### Root Package
*   `MainActivity.kt`: Setup activity for permissions.
*   `MainApplication.kt`: Application class, initializes ViewModel.

### services/
*   `IdeazOverlayService.kt`: The main engine. Manages the system overlay window and hosts the Compose UI.
*   `IdeazAccessibilityService.kt`: Background service for inspecting the foreground UI.
*   `ScreenshotService.kt`: Service for capturing screen content (MediaProjection).

### ui/
*   `IdeBottomSheet.kt`: The primary UI composable. Displays the log list and controls.
*   `MainViewModel.kt`: Logic controller. Connects data to UI.
*   `ContextlessChatInput.kt`: Simple text input composable (renaming to `LogSearchInput` recommended).

### ui/delegates/
*   `StateDelegate.kt`: State holder. Manages `systemLog` flow and batching.

### ui/inspection/
*   `OverlayView.kt`: Custom View for drawing highlighters/rectangles on the screen (used by Accessibility/Screenshot services).

### utils/
*   `LogcatReader.kt`: Reads system logs.
*   `ComposeLifecycleHelper.kt`: Critical utility for running Compose in a Service.
*   `VersionUtils.kt`: Version string parsing.
