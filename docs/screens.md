# Screens

Since this is an overlay app, "screens" are states of the bottom sheet.

## Main Activity
*   **Purpose:** Initial setup, permission request wizard.
*   **State:** Static info.

## Crash Log Activity
*   **Purpose:** Opens automatically when a monitored app crashes while the service runs. Shows the sheet's full view on the crashed app's tab.
*   **Fallback:** A heads-up "<pkg> crashed" notification is always posted (Android 15+ may silently block the background launch); tapping it opens the same screen. If LogKitty is already in front, it only switches tabs.
*   **Exit:** Close button or Back.

## Bottom Sheet
*   **Collapsed/Hidden:** Not visible.
*   **Peek:** Shows last log line.
*   **Half-Expanded:** Shows list of logs, filter input.
*   **Fully-Expanded:** Full screen log viewer.
