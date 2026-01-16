# Build Pipeline

## Local Build
The project uses Gradle for building.
*   **Command:** `./gradlew :app:assembleDebug`
*   **Artifact:** `app/build/outputs/apk/debug/LogKitty-*-debug.apk` (Note: APK name configuration might need updating to `LogKitty`).

## CI/CD (GitHub Actions)
*   **Trigger:** Push to `main`.
*   **Steps:**
    1.  Checkout code.
    2.  Set up JDK 21.
    3.  Grant Execute Permission to gradlew.
    4.  Build with Gradle.
    5.  Run Lint/Tests (Future).
    6.  Upload Artifact.

## Dependencies
*   **Kotlin:** 1.9.x / 2.0 (configured via catalog).
*   **Android Gradle Plugin:** 8.x.
*   **Jetpack Compose:** BOM-based versioning.
