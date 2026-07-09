# Android Manifest

##| Permission | Reason | Status |
| :--- | :--- | :--- |
| `SYSTEM_ALERT_WINDOW` | Core feature: Required to draw the logcat overlay UI on top of other apps. | Essential. Requested on first launch. |
| `READ_LOGS` | Core feature: Required to read the system logcat buffer. | Essential. Requires ADB grant by the user (`adb shell pm grant...`). |
| `FOREGROUND_SERVICE` | Core feature: Required to keep the service running in the background while the UI is visible. | Essential. Automatically granted at install. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Modern Android: Specific type for our service, required by Android 14+. | Essential. Automatically granted at install. |
| `com.android.vending.BILLING` | Monetization: Required to offer the "Ad-Free" in-app purchase. | Essential. Automatically granted at install via Google Play. |

## Components
*   `MainActivity`: Launcher.
*   `IdeazOverlayService`: `foregroundServiceType="specialUse"`.
*   `IdeazAccessibilityService`: Accessibility service configuration.
