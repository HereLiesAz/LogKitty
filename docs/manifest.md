# Android Manifest

##| Permission | Reason | Status |
| :--- | :--- | :--- |
| `SYSTEM_ALERT_WINDOW` | Core feature: Required to draw the logcat overlay UI on top of other apps. | Essential. Requested on first launch. |
| `READ_LOGS` | Core feature: Required to read the system logcat buffer. | Essential. Requires ADB grant by the user (`adb shell pm grant...`). |
| `FOREGROUND_SERVICE` | Core feature: Required to keep the service running in the background while the UI is visible. | Essential. Automatically granted at install. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Modern Android: Specific type for our service, required by Android 14+. | Essential. Automatically granted at install. |

## Components
*   `MainActivity`: Launcher.
*   `CrashLogActivity`: Non-exported, own task affinity, excluded from recents. Started from the overlay service on a monitored-app crash (background start allowed via `SYSTEM_ALERT_WINDOW`).
*   `FileSaverActivity`: Non-exported, translucent; runs the system "save as" picker.
*   `.services.LogKittyOverlayService`: Non-exported, `foregroundServiceType="specialUse"`.
*   `androidx.core.content.FileProvider`: Non-exported; shares saved session logs (`files/logs/`).
