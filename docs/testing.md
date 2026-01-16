# Testing Strategy

## Unit Tests
*   **Target:** ViewModels (`MainViewModel`), Utils (`LogcatReader` logic, though mocking `Runtime.exec` is hard).
*   **Tools:** JUnit 4/5, MockK.

## UI Tests
*   **Target:** `IdeBottomSheet`.
*   **Tools:** Compose UI Test.
*   **Note:** Testing overlays is difficult with Espresso/Compose Test. Focus on testing the *composable* in isolation.

## Manual Verification
1.  Install app.
2.  Grant permissions.
3.  Verify overlay appears.
4.  Generate logs in another app (e.g., open Settings).
5.  Verify logs appear in LogKitty.
