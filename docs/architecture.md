# Architecture

LogKitty follows a refined **MVVM (Model-View-ViewModel)** architecture, specifically adapted for a robust, Service-based overlay application.

## Core Components

### 1. Service Layer (The Host)
* **`LogKittyOverlayService`**: The application's true entry point. It manages the `ComposeView` and the system `WindowManager`.
    * **Responsibility**: It handles the delicate negotiation of window flags (`FLAG_NOT_TOUCHABLE`), managing the overlay's pass-through capabilities. It dynamically resizes the underlying window container based on the `LogBottomSheet` state to ensure the overlay never blocks touches it shouldn't.
* **`LogKittyAccessibilityService`**: A focused background service.
    * **Responsibility**: strictly limited to detecting `TYPE_WINDOW_STATE_CHANGED` events to identify the current foreground package. This powers the "Contextual Logging" feature. It contains **no** UI inspection or node traversal logic.

### 2. UI Layer (View)
* **`LogBottomSheet`**: The primary UI. A pure Jetpack Compose component that renders the log stream, tabs, and control surfaces.
* **`MainActivity`**: Serves as the configuration hub and permission wizard (Overlay, Root).
* **`FileSaverActivity`**: A transient component responsible for writing the *filtered* log buffer to disk via the Android Storage Access Framework.

### 3. ViewModel Layer
* **`MainViewModel`**: The brain of the operation.
    * **Responsibility**: It bridges the Service and Data layers. It observes `LogcatReader`, pushes data into `StateDelegate`, and applies complex transformations (Regex filtering, Tab separation, Tag prohibition) before emitting state to the UI.

### 4. Data & Utility Layer
* **`LogcatReader`**: A hardened singleton.
    * **Mechanism**: Uses `ProcessBuilder` to spawn a `logcat` shell process. It features an internal heartbeat and retry loop to automatically resurrect the stream if the system kills the logcat process.
* **`StateDelegate`**: The single source of truth for the raw log buffer.
* **`UserPreferences`**: Handles persistence of settings (Opacity, Filters, Colors) via `DataStore`.

## Data Flow

1.  **Ingestion**: `LogcatReader` streams raw text from the shell -> `StateDelegate` (Circular Buffer).
2.  **Processing**: `MainViewModel` combines the Raw Buffer with `UserPreferences` (Filters/Tabs).
3.  **Presentation**: `LogBottomSheet` observes the `filteredSystemLog` StateFlow and renders the list.

## Key Design Decisions

* **Dynamic Window Resizing**: Unlike standard overlays that cover the screen and intercept all touches (or none), LogKitty dynamically adjusts its window height. When collapsed, the window physically shrinks to the bottom of the screen, guaranteeing 0% interference with the rest of the device.
* **Hardened IO**: The log reader does not trust the OS. It assumes the stream will die and is built to recover silently without crashing the UI.
* **Context Isolation**: The "Current App" logic is decoupled. If the Accessibility Service is disabled, the app functions perfectly as a global logger, merely hiding the "App" tabs.
