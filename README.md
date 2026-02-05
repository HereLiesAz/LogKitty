# LogKitty 

**The Always-On Logcat Overlay for Android.**

LogKitty is a developer tool that puts your system logs right where you need them: floating above your target app. No more switching back and forth between your device and Android Studio.

## Features

*   **Always-On Overlay:** A persistent bottom sheet that floats over other apps.
*   **Real-Time Logs:** Streams `logcat` output directly to the overlay.
*   **Peek & Expand:**
    *   **Peek:** See the latest log line without obstruction.
    *   **Half:** Scroll through recent logs.
    *   **Full:** Deep dive into stack traces.
*   **Context Awareness:** Automatically detects the foreground app to highlight or filter relevant logs.
*   **Copy to Clipboard:** Quickly grab logs to share or analyze.
*   **Save to File:** Save the current log buffer to a text file for sharing or later analysis.
*   **Settings:** Manage overlay opacity, text size, prohibited tags, and more.

## Documentation

Comprehensive documentation is available in the `docs/` directory:

*   **[Setup Guide](docs/SETUP.md):** Build instructions, prerequisites, and installation.
*   **[Architecture](docs/architecture.md):** High-level overview of the system design (MVVM, Services).
*   **[API Reference](docs/API.md):** Detailed description of key classes and components.
*   **[File Descriptions](docs/file_descriptions.md):** A map of the project structure.
*   **[UI/UX Guidelines](docs/UI_UX.md):** Design philosophy and component breakdown.
*   **[Task Flow](docs/task_flow.md):** Typical user workflows.
*   **[Contribution Guide](docs/conduct.md):** Code of conduct and rules for contributors/agents.

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
5.  Use the icons in the overlay header to **Copy**, **Save**, or open **Settings**.

## Development

LogKitty is built with Kotlin and Jetpack Compose.

### Build
```bash
./gradlew :app:assembleDebug
```

### Versioning
See `version.properties` for the current version state.
