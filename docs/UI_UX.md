# UI/UX Guidelines

## Philosophy
LogKitty is a developer tool designed to be unobtrusive yet instantly accessible. It lives "above" other apps, providing insight without interrupting workflow.

## Color Palette
- **Background:** Dark Grey/Black (`#1E1E1E` in dark mode) for minimal distraction.
- **Text:** High contrast white/grey.
- **Accents:** Minimal use of color; system colors used for specific log levels (Error=Red, Warn=Yellow) if implemented.

## Components
- **Bottom Sheet:** The primary interaction point. It supports four detents (heights include the system navigation bar area):
    - **Hidden (Collapsed):** A thin one-line strip showing the latest log entry; tap to step up.
    - **Peek:** Shows the last four log lines (sized so at least three stay visible above the nav bar).
    - **Half / Fully-Expanded:** Tabs + the scrollable log list (50% / 90% of the screen) for deep debugging.
    - Log line text is rendered at a forced font scale so a large system font setting can't shrink the visible line count or overflow the strips.
- **Overlay:** A transparent touch-through layer that allows interaction with the app below when the sheet is collapsed.
- **App Monitoring:** From Settings → "Monitor specific apps", pick an installed app to pin a dedicated tab showing only that app's logs (filtered by the app's UID), alongside the full System log.
- **Notification:** The ongoing foreground notification offers a one-tap "Turn Off LogKitty" action (and body tap) that stops the service and removes the overlay.

## Interaction
- **Drag:** Users can drag the sheet up/down to change states; dragging down collapses straight to Hidden.
- **Tap:** Tapping the peek view expands it.
- **Copy:** A dedicated button allows copying visible logs to the clipboard.
- **Clear:** A button to clear the current log buffer.
