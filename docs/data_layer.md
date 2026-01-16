# Data Layer

## Log Data
*   **Source:** System `logcat` buffer.
*   **Access:** Shell command `logcat -v time`.
*   **Storage:** In-memory circular buffer (managed by `StateDelegate`).
*   **Persistence:** None. Logs are lost when the service stops.

## Preferences
*   **Storage:** `SharedPreferences` (via `SettingsViewModel` - *To be refactored/simplified*).
*   **Data:**
    *   Theme preference (Dark/Light).
    *   Overlay position (Future).
    *   Last prompt (Future).
