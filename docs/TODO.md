# TODO Roadmap

## 1. Implement Robust Error Handling
Ensure the app behaves gracefully when permissions are denied or services are interrupted.
- [ ] **Permission Management**
    - [ ] Update `MainActivity` to check for `SYSTEM_ALERT_WINDOW` (Overlay) and `READ_LOGS` permissions on every resume.
    - [ ] Implement a `PermissionRationaleDialog` to explain *why* permissions are needed before requesting them.
    - [ ] Handle "Don't ask again" scenarios by directing the user to App Settings.
    - [ ] Add a check for `AccessibilityService` status using `AccessibilityManager.getEnabledAccessibilityServiceList()`.
- [ ] **Service Lifecycle**
    - [ ] Wrap `startForegroundService` calls in `try-catch` blocks to handle `ForegroundServiceStartNotAllowedException` (Android 12+).
    - [ ] Implement a broadcast receiver in `MainActivity` to listen for service death/crash and update UI state accordingly.
- [ ] **Log Reading**
    - [ ] Handle `IOException` in `LogcatReader` when the `logcat` process dies or is killed.
    - [ ] Implement automatic retry logic with exponential backoff for the log reader.

## 2. Add Settings Screen
Create a dedicated screen to configure application behavior and appearance.
- [ ] **UI Implementation**
    - [ ] Create `SettingsViewModel` to manage preferences state.
    - [ ] Add `SettingsScreen` composable with sections: "Appearance", "Logging", "System".
- [ ] **Features to Configure**
    - [ ] **Appearance:**
        - [ ] Slider for `Overlay Transparency` (alpha value 0.1 - 1.0).
        - [ ] Slider for `Text Size` (SP).
        - [ ] Toggle for `Dark Mode` (System/Dark/Light).
    - [ ] **Logging:**
        - [ ] Multi-select dropdown for `Log Levels` (Verbose, Debug, Info, Warn, Error, Assert).
        - [ ] Text field for `Global Regex Filter`.
        - [ ] Toggle for `Show Timestamp`.
- [ ] **Integration**
    - [ ] Observe settings in `LogBottomSheet` and `LogKittyOverlayService` to apply changes in real-time.

## 3. Implement "Contextual Logging"
Refine the feature that filters logs based on the app currently in the foreground.
- [ ] **Service Logic**
    - [ ] Verify `LogKittyAccessibilityService` reliably broadcasts `ACTION_FOREGROUND_APP_CHANGED`.
    - [ ] Debounce the broadcast to avoid rapid switching during animations/transitions.
- [ ] **ViewModel Logic**
    - [ ] Update `MainViewModel` to allow "Soft Context" (highlight logs from foreground app) vs "Hard Context" (hide others).
    - [ ] Add a list of "Ignored Packages" (e.g., Pixel Launcher, System UI) to prevent context switching on home screen.
- [ ] **UI Indicators**
    - [ ] Add a pill/badge in the `LogBottomSheet` header showing the current tracked app (e.g., "Tracking: com.example.app").

## 4. Improve UI/UX
Enhance the polish and usability of the bottom sheet.
- [ ] **Visuals**
    - [ ] Add a visible `DragHandle` at the top of the bottom sheet.
    - [ ] Apply a blur effect (Android 12+) or dimming behind the sheet when fully expanded.
- [ ] **Interactions**
    - [ ] Implement "Snap to Bottom" button that appears when the user scrolls up (pausing auto-scroll).
    - [ ] Add swipe-to-dismiss support for individual log lines (optional, maybe to "ignore" a tag).
    - [ ] Add a "Search/Find in Page" bar for the current buffer.

## 5. Add Persistence
Persist user preferences so they survive app restarts.
- [ ] **DataStore Integration**
    - [ ] Add `androidx.datastore:datastore-preferences` dependency.
    - [ ] Create `UserPreferencesRepository` class.
- [ ] **State to Persist**
    - [ ] `overlay_transparency` (Float)
    - [ ] `text_size` (Int)
    - [ ] `context_mode_enabled` (Boolean)
    - [ ] `saved_filters` (Set<String>)
- [ ] **ViewModel Integration**
    - [ ] Inject repository into `MainViewModel` (or `SettingsViewModel`).
    - [ ] Convert `StateFlow`s in ViewModel to be backed by DataStore flows.

## 6. Add Tests
Establish a testing baseline for reliability.
- [ ] **Unit Tests (`app/src/test`)**
    - [ ] `LogcatReaderTest`: Mock `Runtime` and `Process` to verify log parsing regex.
    - [ ] `StateDelegateTest`: Verify batching logic and circular buffer (`MAX_LOG_SIZE`) behavior.
    - [ ] `MainViewModelTest`: Test filtering logic (Contextual vs Global).
- [ ] **UI Tests (`app/src/androidTest`)**
    - [ ] `SettingsScreenTest`: Verify switches toggle states.
    - [ ] `LogBottomSheetTest`: Verify expanding/collapsing sheet.
- [ ] **Integration Tests**
    - [ ] Test `LogKittyAccessibilityService` broadcasts using a mock receiver.
