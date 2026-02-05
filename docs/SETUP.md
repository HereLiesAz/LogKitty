# Setup & Installation Guide

This guide covers how to build, install, and configure LogKitty for development.

## Prerequisites

*   **Java Development Kit (JDK):** Version 21.
*   **Android SDK:**
    *   Compile SDK: 36
    *   Min SDK: 30
*   **Android Studio:** Koala or later recommended.

## Building the Project

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/HereLiesAz/LogKitty.git
    cd LogKitty
    ```

2.  **Build Debug APK:**
    Run the Gradle wrapper command from the root directory:
    ```bash
    ./gradlew :app:assembleDebug
    ```
    The output APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

1.  **Install via ADB:**
    ```bash
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ```

2.  **Grant Runtime Permissions:**
    LogKitty requires special permissions to function.
    *   **Overlay Permission:** Launch the app and follow the prompt to "Allow display over other apps".
    *   **Read Logs Permission:** This is a protected system permission and **must** be granted via ADB for every installation:
        ```bash
        adb shell pm grant com.hereliesaz.logkitty android.permission.READ_LOGS
        ```

3.  **Optional: Root Access**
    If your device is rooted, you can enable "Root Mode" in settings to read logs without the ADB permission (via `su`).

## Environment Configuration

### GitHub Integration (Crash Reporting)
The app can report crashes to GitHub Issues. To enable this, set the following environment variable during build time (or in your `local.properties`):

*   `GH_TOKEN`: A GitHub Personal Access Token with repo permissions.

### Google Fonts
To use Google Fonts, you need an API key.
*   `FONTS_API_KEY`: Add this to `local.properties` if you want to modify font providers.

## Troubleshooting

*   **"Permission Denied" reading logs:** Ensure you executed the `adb shell pm grant` command.
*   **Overlay not showing:** Ensure the "Display over other apps" permission is granted in Android Settings -> Apps -> LogKitty.
*   **Build fails on JDK:** Verify your `JAVA_HOME` points to JDK 21.
