# Blueprint & Roadmap

## Vision
To become the ultimate "heads-up display" for Android developers, effectively replacing the need to constantly switch between the device and Android Studio's Logcat window during UI testing.

## Phases

### Phase 1: MVP (Current)
- [x] Overlay Service.
- [x] Basic Logcat streaming.
- [x] Bottom Sheet UI.
- [x] Copy/Clear functionality.

### Phase 2: Context Awareness
- [ ] Detect foreground application package name.
- [ ] Filter logcat stream to show only the foreground app's logs.
- [ ] Highlight "Error" and "Fatal" logs visually.

### Phase 3: Interactivity
- [ ] Allow users to input shell commands via the text box.
- [ ] "Select Mode": Inspect UI elements of the underlying app (using Accessibility Node info).

### Phase 4: Intelligence
- [ ] AI Integration: Analyze the visible error log and suggest fixes (LogKitty -> Jules/Gemini).
