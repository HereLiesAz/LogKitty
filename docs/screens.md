# Screens

Since this is an overlay app, "screens" are states of the bottom sheet.

## Main Activity
*   **Purpose:** Initial setup, permission request wizard.
*   **State:** Static info.

## Crash Log Activity
*   **Purpose:** Opens automatically when a monitored app crashes while the service runs. Shows the sheet's full view on the crashed app's tab.
*   **Fallback:** A heads-up "<pkg> crashed" notification is always posted (Android 15+ may silently block the background launch); tapping it opens the same screen. If LogKitty is already in front, it only switches tabs.
*   **Exit:** Close button or Back.

## Context Mode sections
*   With Context Mode on, the general tabs (All, Errors) are split into one section per app visited, in order. Switching apps starts a new section; earlier sections stay.
*   Each section header shows the app name and line count, with **Copy** (that app's lines only) and **Delete** (removes that section's lines so far; new lines from that app still appear).
*   Hard Context Mode keeps only each section's own app's lines. A new section is backdated to the app's first recent line, so launch logging isn't lost.

## Bottom Sheet
*   **Collapsed/Hidden:** Not visible.
*   **Peek:** Shows last log line.
*   **Half-Expanded:** Shows list of logs, filter input.
*   **Fully-Expanded:** Full screen log viewer.
