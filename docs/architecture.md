# Architecture

LogKitty follows a simplified **MVVM (Model-View-ViewModel)** architecture, adapted for a Service-based overlay application.

## Core Components

1.  **Service Layer (The Host)**
    *   `IdeazOverlayService`: The entry point. It creates a `ComposeView` and attaches it to the system `WindowManager`. It acts as the "Activity" for the overlay UI.
    *   `IdeazAccessibilityService`: Runs in the background to listen for system events (like window changes) to potentially filter logs by the active app.

2.  **UI Layer (View)**
    *   `IdeBottomSheet`: A pure Jetpack Compose component rendering the log list and controls.
    *   `MainActivity`: Currently serves as a launcher/setup screen to request permissions, but the main experience is in the service.

3.  **ViewModel Layer**
    *   `MainViewModel`: Holds the state (`StateDelegate`) and business logic. It survives configuration changes of the activity but is tied to the Application lifecycle for the Service.

4.  **Data/Utility Layer**
    *   `LogcatReader`: A singleton/object that runs a shell command (`logcat`) and exposes a `Flow<String>` of log lines.
    *   `StateDelegate`: Manages the application state (log buffers, UI states) to ensure a single source of truth.

## Data Flow
`LogcatReader` -> (Flow) -> `MainViewModel` -> `StateDelegate` -> (StateFlow) -> `IdeBottomSheet` (Compose UI)
