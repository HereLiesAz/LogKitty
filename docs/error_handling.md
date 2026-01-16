# Error Handling

## Strategies
1.  **Fail Safe:** The overlay service should never crash the host OS or interfere with the target app. Exceptions in the drawing loop are caught and logged.
2.  **User Feedback:**
    *   **Permissions:** If `READ_LOGS` or `SYSTEM_ALERT_WINDOW` is missing, show a Toast or UI prompt directing the user to settings.
    *   **Logcat Failure:** If `logcat` command fails (e.g., permission denied), display a specific error message in the bottom sheet stream.

## Known Exceptions
*   `WindowManager.BadTokenException`: Occurs if the service tries to add a view when the context is invalid or permission is revoked. Handled by try-catch blocks in `IdeazOverlayService`.
