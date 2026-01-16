# Android Manifest

## Permissions
*   `SYSTEM_ALERT_WINDOW`: Required to draw the overlay.
*   `READ_LOGS`: Required to read system logs from other apps. **Note:** This is a protected permission. On production builds, it must be granted via ADB: `adb shell pm grant com.hereliesaz.logkitty android.permission.READ_LOGS`.
*   `BIND_ACCESSIBILITY_SERVICE`: Required for the accessibility service.
*   `FOREGROUND_SERVICE`: Required to keep the overlay and listener active.

## Components
*   `MainActivity`: Launcher.
*   `IdeazOverlayService`: `foregroundServiceType="specialUse"`.
*   `IdeazAccessibilityService`: Accessibility service configuration.
