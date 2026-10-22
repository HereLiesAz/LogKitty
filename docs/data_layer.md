# Data Layer

## Log Data
*   **Source:** System `logcat` buffer.
*   **Access:** Shell command `logcat -v time`.
*   **Storage:** In-memory circular buffer (managed by `StateDelegate`).
*   **Deobfuscation:** Before buffering, lines from apps with a loaded mapping are retraced (`MappingStore`); mappings live in `files/mappings/<pkg>.txt`.
*   **Persistence:** None. Logs are lost when the service stops.

## Preferences
*   **Storage:** `SharedPreferences` (via `SettingsViewModel` - *To be refactored/simplified*).
*   **Data:**
    *   Theme preference (Dark/Light).
    *   Overlay position (Future).
    *   Last prompt (Future).
