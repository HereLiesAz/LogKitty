# File Descriptions

## Root
*   `AGENTS.md`: Master instructions and index for AI agents.
*   `build.gradle.kts`: Project-level build configuration.
*   `settings.gradle.kts`: Module inclusion settings.
*   `gradle.properties`: Gradle build properties.
*   `version.properties`: Source of truth for project versioning. Central version contract keys (`versionMajor`/`versionMinor`/`versionPatch`/`versionBuild`); the central release workflows record the published pair here. Local builds count in the untracked `.local-build-number`.
*   `get_version.sh`: Script to extract version info (major.minor.patch.build).
*   `lint-baseline.xml`: Baseline file for lint warnings.
*   `proguard-rules.pro`: ProGuard/R8 configuration rules.

## app/src/main/kotlin/com/hereliesaz/logkitty/

### Root Package
*   `MainActivity.kt`: The entry point activity. Handles initial setup, permission requests (Overlay, Root), and service starting.
*   `MainApplication.kt`: The Application class. Initializes global singletons like `MainViewModel`.
*   `CrashLogActivity.kt`: Full-screen log view (the `LogBottomSheet` pinned at FULL) opened by the overlay service when a monitored app crashes, since the overlay is disabled on the launcher a crash lands on. Collapsing it finishes the activity.
*   `FileSaverActivity.kt`: A transient Activity used to save log buffers to a file using the System File Picker.

### services/
*   `LogKittyOverlayService.kt`: The core service. Manages the system overlay window, handles window resizing/pass-through logic, and hosts the Compose UI.
*   `DebugAppMonitor.kt`: Polls `debug_app` (Developer options → Select debug app) for Follow Debug App and opens that app's tab.
*   `ForegroundAppMonitor.kt`: Polls `UsageStatsManager` (non-root) or `dumpsys` (root) for the foreground app and broadcasts changes for Context Mode.

### nativecrash/
*   `ElfSymbols.kt`: Reads an ELF `.so` (32/64-bit LE): function symbols from `.symtab` (else `.dynsym`), Thumb-bit aware, plus the GNU build-id; `lookup(pc)` → `name+0xoff`.
*   `NativeFrame.kt`: Parses a crash-dump backtrace frame (`#NN pc … lib.so (sym) (BuildId: …)`), including `base.apk!lib` paths and ABI.
*   `NativeSymbolStore.kt`: Per-app symbol files in `files/symbols/<pkg>/<abi>/`, from loose `.so`/`.so.dbg` or AGP's `native-debug-symbols.zip`; annotates frames with `[symbol]` on ingest, checking build-ids.
*   `TombstoneCollector.kt`: Root only — after a monitored app's native crash, saves the full `/data/tombstones` entry, symbolicated, to the logs dir.
*   `BacktraceFolding.kt`: Folds stack traces longer than 5 frames into "… N more frames" rows in the log list.
### retrace/
*   `MappingFile.kt`: Parses an R8/ProGuard `mapping.txt` into an obfuscated-class index (class renames, R8 `sourceFile` metadata, method line ranges and inline chains).
*   `Retracer.kt`: Rewrites `at cls.method(File:line)` frames and obfuscated exception class names; inlined frames expand to several lines, each keeping the logcat header.
*   `MappingStore.kt`: Per-app mappings in `files/mappings/<pkg>.txt`; matches log lines to apps by UID and retraces them on ingest.
### privilege/
*   `ShizukuShell.kt`: Shizuku status, permission handshake, and `sh -c` execution as the ADB `shell` user (reflective `newProcess`, kept by `proguard-rules.pro`).
*   `PrivilegeGranter.kt`: Self-grants `DUMP`, usage access and `READ_LOGS` via root or Shizuku. A new READ_LOGS grant runs detached and ends with `am force-stop` + `am start`, relaunching LogKitty into a process that carries the `log` gid.

### update/
*   `UpdateChecker.kt`: Finds the update for this install — Play in-app update API when installed by Google Play, otherwise the GitHub Releases API (stable `vX.Y.Z.B` tags for release builds, `latest-debug-vX.Y` for debug builds) — and opens the Play listing or release page.
*   `UpdateDialog.kt`: "Update available" dialog naming the source, with an Open button.

### ui/
*   `LogBottomSheet.kt`: The primary UI composable. Custom 4-detent overlay (HIDDEN shows one line, PEEK the last four, HALF/FULL the full list) with tab row, gesture zones, and selectable log items. Forces `fontScale = 1` on log lines so the rendered line height always matches the detent strip sizing regardless of the device's system font setting.
*   `ContextSegments.kt`: Context Mode history — one segment per app visited (`pkg`, `startId`); pure helpers to map lines to segments, group them for section headers, and append/trim segments.
*   `LogCategory.kt`: The app tab's quick-filter chips (Crashes, Errors, Warnings, Network, Memory, ANRs) as text/level predicates.
*   `AnalyzerDashboardScreen.kt`: The main home screen dashboard of the app. Displays detailed device and app statistics (RAM, Storage, CPU, Battery, OS information) with custom Compose progress animations and a scrollable list of persisted log files.
*   `SheetController.kt`: Shared state holder for the active detent — consumed by both the Compose UI (for animation) and the hosting Service (for window sizing).
*   `MainViewModel.kt`: The central logic controller. Bridges the `LogcatReader` data, `UserPreferences`, and the UI. Handles per-tab clearing, side-swipe tab navigation, and pinned per-app tabs filtered reliably by the app's UID.
*   `SettingsScreen.kt`: A dedicated screen for configuring app behavior. Hosts navigation into the prohibited-tags list, the color scheme editor, preferences export/import, and the "Monitor specific apps" picker.
*   `AppPickerDialog.kt`: A searchable dialog listing installed apps (label + icon); returns the chosen package so it can be pinned for a dedicated, UID-filtered log tab.
*   `ProhibitedLogsScreen.kt`: UI for managing the list of prohibited log tags/strings.
*   `ColorSchemeEditorScreen.kt`: Per-level color customization. Selecting any swatch flips the active scheme to CUSTOM.
*   `LogColors.kt`: Log level enum plus the built-in `LogColorScheme` palettes (Material, AOSP, Pidcat, Monochrome, Solarized, Custom) and the tag-based highlight rules.
*   `ColorPickerDialog.kt`: A dialog composable for picking colors (used in settings).

### ui/delegates/
*   `StateDelegate.kt`: The data holder and processor. Tags every log line with a monotonically-increasing `IndexedLogLine.id` (the basis for per-tab clearing) and the emitting process's `uid` (parsed from the `-v uid` column, for per-app filtering), batches incoming lines, and caps the rolling buffer.

### ui/theme/
*   `Theme.kt`: Jetpack Compose theme definition.
*   `Color.kt`: Color palette definitions.
*   `Type.kt`: Typography definitions.

### utils/
*   `LogcatReader.kt`: The engine that spawns and reads the `logcat` process. Handles stream parsing and resilience.
*   `CrashDetector.kt`: Pure recognizer for app-crash lines (`FATAL EXCEPTION` by UID, `AndroidRuntime: Process: <pkg>`, native tombstone `>>> <pkg> <<<`). Feeds `MainViewModel.crashEvent`.
*   `ComposeLifecycleHelper.kt`: Critical utility for bridging the gap between an Android Service and Jetpack Compose's Lifecycle-aware components.
*   `UserPreferences.kt`: Manages persistence of user settings (DataStore/SharedPreferences) and export/import functionality.

