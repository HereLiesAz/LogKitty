# TODO Roadmap & Tactical Implementation Plan

## 1. Runtime Hardening & Safety Rails
**Goal:** Prevent crashes and guide the user through the "Hostile" Android permission landscape.

### 1.1 Permission Management
- [ ] **Implement `READ_LOGS` Check in `MainActivity`**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/MainActivity.kt`.
    - [ ] Locate or create the `onResume()` lifecycle method.
    - [ ] Add code to call `ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_LOGS)`.
    - [ ] Create a conditional check: If result is `PERMISSION_DENIED`:
        - [ ] Instantiate a **non-dismissible** `AlertDialog` (or Composable Dialog).
        - [ ] Set Dialog Title: "ADB Permission Required".
        - [ ] Set Dialog Body: "Android does not allow apps to read logs without special permission. You must grant this via ADB."
        - [ ] Display the exact command string: `adb shell pm grant com.hereliesaz.logkitty android.permission.READ_LOGS`.
        - [ ] Add a "Copy Command" button to the dialog.
        - [ ] Implement the "Copy Command" button logic to copy the string to the system clipboard.

- [ ] **Implement Service Accessibility Check**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/MainActivity.kt`.
    - [ ] Create a new private method named `checkAccessibilityPermission()`.
    - [ ] Inside the method, get the `AccessibilityManager` system service.
    - [ ] Call `getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)`.
    - [ ] Iterate through the returned list of services.
    - [ ] Check if `resolveInfo.serviceInfo.packageName` equals the app's `packageName`.
    - [ ] Check if `resolveInfo.serviceInfo.name` contains `LogKittyAccessibilityService`.
    - [ ] Store the result (found/not found) in a boolean state variable.
    - [ ] Update the UI: If the service is missing (false), show an "Enable Context Tracking" button.
    - [ ] Add an `onClick` listener to the button.
    - [ ] Inside the listener, call `startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))`.

### 1.2 Service Lifecycle Robustness
- [ ] **Safe Foreground Service Start**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/MainActivity.kt`.
    - [ ] Locate the `startOverlayService()` function.
    - [ ] Wrap the `startForegroundService(intent)` call in a `try` block.
    - [ ] Add a `catch` block for `android.app.ForegroundServiceStartNotAllowedException`.
    - [ ] Inside the `catch` block, show a `Toast` with the text: "App is in background. Open app to start overlay."

- [ ] **Service Death Monitoring**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/MainActivity.kt`.
    - [ ] Create a `BroadcastReceiver` object.
    - [ ] In the receiver's `onReceive`, listen for `ACTION_STOP_SERVICE` (ensure this action is defined in `OverlayService`).
    - [ ] Inside `onReceive`, set the UI state variable `isOverlayRunning` to `false`.
    - [ ] Register this receiver in `onResume` (or `onCreate`).
    - [ ] Unregister this receiver in `onPause` (or `onDestroy`).

### 1.3 Log Reader Resilience
- [ ] **Hardened Process Loop**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/utils/LogcatReader.kt`.
    - [ ] Replace any `Runtime.exec` calls with `ProcessBuilder`.
    - [ ] Locate the `reader.readLine()` loop.
    - [ ] Wrap the entire `while` loop logic in a `try` block.
    - [ ] Add a `catch` block for `IOException`.
    - [ ] Inside the `catch` block, call `emit("Logcat stream died. Retrying...")`.
    - [ ] Inside the `catch` block, add `delay(2000)` to prevent tight-loop crashing.
    - [ ] Ensure the outer `while(isActive)` loop logic will restart the `ProcessBuilder` sequence after the `catch` block finishes.

---

## 2. Settings Screen & Configuration
**Goal:** Allow granular control over the UI and log behaviors via a dedicated screen.

### 2.1 Settings UI Structure
- [ ] **Create `SettingsViewModel`**
    - [ ] Create file `app/src/main/kotlin/com/hereliesaz/logkitty/ui/SettingsViewModel.kt`.
    - [ ] Define `SettingsViewModel` class inheriting from `ViewModel`.
    - [ ] Inject `UserPreferences` repository into the constructor.
    - [ ] Expose a `StateFlow` for `overlayOpacity`.
    - [ ] Expose a `StateFlow` for `textSize`.
    - [ ] Expose a `StateFlow` for `bufferSize`.
    - [ ] Expose a `StateFlow` for `ignoredPackages`.

- [ ] **Create `SettingsScreen` Composable**
    - [ ] Create file `app/src/main/kotlin/com/hereliesaz/logkitty/ui/SettingsScreen.kt`.
    - [ ] Define the `SettingsScreen` composable function.
    - [ ] Add a `Scaffold` as the root element.
    - [ ] Add a `Column` with `Modifier.verticalScroll(rememberScrollState())` inside the Scaffold.
    - [ ] Create a "Appearance" section header Text.
    - [ ] Create a "Logging Behavior" section header Text.
    - [ ] Create a "System" section header Text.

### 2.2 Features Implementation
- [ ] **Appearance Controls**
    - [ ] Add a Slider for Opacity.
        - [ ] Set value range to `0.1f`..`1.0f`.
        - [ ] Bind value to `UserPreferences.overlayOpacity`.
    - [ ] Add a Slider for Text Size.
        - [ ] Set value range to `8f`..`24f`.
        - [ ] Bind value to a new `UserPreferences.textSize` preference.
    - [ ] Add a Toggle/Switch for Dark Mode.
        - [ ] Bind value to `UserPreferences.themeMode` (System/Dark/Light).

- [ ] **Logging Controls**
    - [ ] Add a "Log Levels" Multi-Select Row.
        - [ ] Create Checkboxes for: Verbose, Debug, Info, Warn, Error.
        - [ ] Save the selection as a `Set<String>` in DataStore.
    - [ ] Add a "Show Timestamp" Toggle.
        - [ ] Bind value to a boolean preference.
        - [ ] Implement logic: If false, strip the regex `^\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}` from the displayed log line.
    - [ ] Add a "Buffer Size" Dropdown.
        - [ ] Add options: 1000, 2000, 5000 lines.
        - [ ] Update `StateDelegate.MAX_LOG_SIZE` based on selection.

---

## 3. "Contextual Logging" Refinement
**Goal:** Make the "Current App" filter smarter and less jittery.

### 3.1 Service-Side Filtering
- [ ] **Implement Package Blocklist**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/services/LogKittyAccessibilityService.kt`.
    - [ ] Define a private constant `IGNORED_PACKAGES`.
    - [ ] Initialize it as a Set containing: `com.android.systemui`.
    - [ ] Add `com.google.android.apps.nexuslauncher` to the Set.
    - [ ] Add `com.android.launcher3` to the Set.
    - [ ] Locate the `onAccessibilityEvent` method.
    - [ ] Add a check: `if (packageName in IGNORED_PACKAGES)`.
    - [ ] If true, return immediately (do **not** broadcast `ACTION_FOREGROUND_APP_CHANGED`).

- [ ] **Debounce Broadcasts**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/services/LogKittyAccessibilityService.kt`.
    - [ ] Add a private variable `lastBroadcastTime` initialized to `0L`.
    - [ ] Inside `onAccessibilityEvent`, get `System.currentTimeMillis()`.
    - [ ] Add check: `if (currentTime - lastBroadcastTime < 300) return`.
    - [ ] Update `lastBroadcastTime = currentTime` after the check passes.

### 3.2 ViewModel Logic
- [ ] **Soft vs Hard Context**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/MainViewModel.kt`.
    - [ ] Add a preference `isHardContextMode` (Boolean).
    - [ ] Update the `filteredSystemLog` flow combination logic.
    - [ ] Create logic branch for "Hard Context Mode":
        - [ ] Filter the list to include *only* lines containing `currentForegroundApp`.
    - [ ] Create logic branch for "Soft Context Mode" (New Default):
        - [ ] Do *not* remove lines from other apps.
        - [ ] (Optional) Append metadata to highlight the foreground app lines.

---

## 4. UI/UX Polish
**Goal:** Make the bottom sheet feel native and usable.

### 4.1 Visual Indicators
- [ ] **Add Drag Handle**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/LogBottomSheet.kt`.
    - [ ] Locate the top-level `Column` content inside the sheet.
    - [ ] Add a `Box` composable as the first child.
    - [ ] Set Modifier size to `width=32.dp`, `height=4.dp`.
    - [ ] Set background to `Color.Gray` with `alpha=0.5f`.
    - [ ] Set clip shape to `CircleShape`.

- [ ] **Add Tracking Badge**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/LogBottomSheet.kt`.
    - [ ] Locate the header row (near the "Context Mode" eye icon).
    - [ ] Create a conditional: `if (currentForegroundApp != null)`.
    - [ ] Inside conditional, add a `Text` composable.
    - [ ] Set text content to `"Tracking: $currentForegroundApp"`.
    - [ ] Set style to `MaterialTheme.typography.caption`.

### 4.2 Scroll & Search Interactions
- [ ] **Snap to Bottom FAB**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/LogBottomSheet.kt`.
    - [ ] Calculate visibility condition: `listState.firstVisibleItemIndex < listState.layoutInfo.totalItemsCount - 10`.
    - [ ] Add a `FloatingActionButton` (FAB) to the layout.
    - [ ] Set FAB visibility based on the calculation above.
    - [ ] Add an Icon (Arrow Down) to the FAB.
    - [ ] set FAB `onClick`: `scope.launch { listState.scrollToItem(logs.lastIndex); autoScroll = true }`.

- [ ] **Local Buffer Search**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/LogBottomSheet.kt`.
    - [ ] Define state: `var searchQuery by remember { mutableStateOf("") }`.
    - [ ] Add a `TextField` composable to the expanded header row.
    - [ ] Bind `TextField` value to `searchQuery`.
    - [ ] Create a derived list variable: `val displayLogs`.
    - [ ] Define filter logic: `logs.filter { it.contains(searchQuery, true) }`.
    - [ ] Pass `displayLogs` to the `LazyColumn` items instead of the raw `logs`.

---

## 5. Persistence & State
**Goal:** Ensure settings survive process death.

### 5.1 DataStore Expansion
- [ ] **Add New Keys to `UserPreferences`**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/utils/UserPreferences.kt`.
    - [ ] Define constant `KEY_TEXT_SIZE` (Int).
    - [ ] Define constant `KEY_BUFFER_SIZE` (Int).
    - [ ] Define constant `KEY_IGNORED_PACKAGES` (StringSet).
    - [ ] Define constant `KEY_SHOW_TIMESTAMP` (Boolean).

- [ ] **ViewModel Integration**
    - [ ] Open `app/src/main/kotlin/com/hereliesaz/logkitty/ui/MainViewModel.kt`.
    - [ ] Create a `StateFlow` for `textSize`.
    - [ ] Create a `StateFlow` for `bufferSize`.
    - [ ] Create a `StateFlow` for `ignoredPackages`.
    - [ ] Create a `StateFlow` for `showTimestamp`.
    - [ ] Ensure these flows are exposed publicly for the UI to collect.

---

## 6. Testing Baseline
**Goal:** Prove it works before running it.

- [ ] **Test Log Parsing Regex**
    - [ ] Create test file `app/src/test/kotlin/com/hereliesaz/logkitty/RegexTest.kt`.
    - [ ] Define a test case function `testStandardLogFormat()`.
    - [ ] Create a sample string: `"01-24 12:00:00.000 100 100 D TagName: Message"`.
    - [ ] Write assertion: Verify the Regex extracts "TagName" correctly.
    - [ ] Define a test case function `testFallbackLogFormat()`.
    - [ ] Create a sample string: `"System.err: java.lang.Exception"`.
    - [ ] Write assertion: Verify the fallback logic extracts "System.err".

- [ ] **Test Buffer Limit**
    - [ ] Create test file `app/src/test/kotlin/com/hereliesaz/logkitty/StateDelegateTest.kt`.
    - [ ] Initialize `StateDelegate` with a mocked scope.
    - [ ] Set `MAX_LOG_SIZE` to 10 (via reflection or setter if available).
    - [ ] Run a loop to add 15 log items.
    - [ ] Assert `delegate.systemLog.value.size` equals 10.
    - [ ] Assert `delegate.systemLog.value.first()` equals the 6th item added (FIFO validation).
