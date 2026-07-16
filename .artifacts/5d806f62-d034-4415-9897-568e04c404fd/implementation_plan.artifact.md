# Fix Compilation Errors in SettingsScreen.kt

The `SettingsScreen.kt` file references properties and methods (`autoDeleteDurationDays`, `maxTotalLogSizeMegabytes`, `setAutoDeleteDurationDays`, `setMaxTotalLogSizeMegabytes`) that were not exposed in `MainViewModel.kt` or persisted in `UserPreferences.kt`.

## Proposed Changes

### [Component Name]
#### [MODIFY] [MainViewModel.kt](file:///C:/Users/azrie/StudioProjects/LogKitty/app/src/main/kotlin/com/hereliesaz/logkitty/ui/MainViewModel.kt)
- Expose `autoDeleteDurationDays` and `maxTotalLogSizeMegabytes` as `StateFlow`.
- Add `setAutoDeleteDurationDays` and `setMaxTotalLogSizeMegabytes` methods to delegate to `UserPreferences`.

#### [MODIFY] [strings.xml](file:///C:/Users/azrie/StudioProjects/LogKitty/app/src/main/res/values/strings.xml)
- Define missing strings for settings UI components.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify compilation.

### Manual Verification
- N/A
