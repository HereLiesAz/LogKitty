# LogKitty 🐱

**The Always-On Logcat Overlay for Android.**

LogKitty is a developer tool that puts your system logs right where you need them: floating above your target app. No more switching back and forth between your device and Android Studio.

## Features

*   **Always-On Overlay:** A persistent bottom sheet that floats over other apps.
*   **Real-Time Logs:** Streams `logcat` output directly to the overlay.
*   **Peek & Expand:**
    *   **Peek:** See the latest log line without obstruction.
    *   **Half:** Scroll through recent logs.
    *   **Full:** Deep dive into stack traces.
*   **Copy to Clipboard:** Quickly grab logs to share or analyze.
*   **Context Aware (Beta):** Includes accessibility integration for future foreground app filtering.

## Installation

1.  Download the latest APK from Releases.
2.  Install on your Android device.
3.  **Grant Permissions:**
    *   Follow the in-app prompt to grant "Display Over Other Apps".
    *   **Required:** Grant the `READ_LOGS` permission via ADB:
        ```bash
        adb shell pm grant com.hereliesaz.logkitty android.permission.READ_LOGS
        ```

## Usage

1.  Open **LogKitty**.
2.  Tap "Start Overlay".
3.  Navigate to the app you want to debug.
4.  Watch the logs roll in!

## Development

LogKitty was originally forked from the **IDEaz** project, stripping away the IDE functionality to focus purely on the logcat overlay experience.

### Build
```bash
./gradlew :app:assembleDebug
```

### Documentation
See the `docs/` directory for detailed architecture, design, and contribution guidelines.
