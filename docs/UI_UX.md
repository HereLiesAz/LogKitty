# UI/UX Guidelines

## Philosophy
LogKitty is a developer tool designed to be unobtrusive yet instantly accessible. It lives "above" other apps, providing insight without interrupting workflow.

## Color Palette
- **Background:** Dark Grey/Black (`#1E1E1E` in dark mode) for minimal distraction.
- **Text:** High contrast white/grey.
- **Accents:** Minimal use of color; system colors used for specific log levels (Error=Red, Warn=Yellow) if implemented.

## Components
- **Bottom Sheet:** The primary interaction point. It supports three states (heights include the system navigation bar area):
    - **Hidden (Collapsed):** Small strip at the bottom (2% of screen height + Nav Bar).
    - **Peek:** Small strip at the bottom, showing the last log line or status (25% of screen height + Nav Bar).
    - **Fully-Expanded:** Covers 80% of the screen + Nav Bar for deep debugging.
- **Overlay:** A transparent touch-through layer that allows interaction with the app below when the sheet is collapsed.

## Interaction
- **Drag:** Users can drag the sheet up/down to change states.
- **Tap:** Tapping the peek view expands it.
- **Copy:** A dedicated button allows copying visible logs to the clipboard.
- **Clear:** A button to clear the current log buffer.
