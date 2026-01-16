# Performance

## Log Streaming
*   **Batching:** Logs are batched in `StateDelegate` (every 100ms) to avoid overwhelming the UI thread with thousands of recompositions per second.
*   **LazyColumn:** Uses `LazyColumn` for efficient rendering of large lists.
*   **Capping:** The log buffer is capped (e.g., 1000 lines) to prevent OOM errors.

## Overlay
*   **Touch Passthrough:** When the sheet is in "Peek" mode or collapsed, the overlay window must be `FLAG_NOT_TOUCHABLE` so it doesn't block interaction with the underlying app.
*   **Hardware Acceleration:** Enabled for the Compose view.
