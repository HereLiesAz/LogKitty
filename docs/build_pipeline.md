# Build Pipeline

## Local Build
The project uses Gradle for building.
*   **Command:** `./gradlew :app:assembleDebug`
*   **Artifact:** `app/build/outputs/apk/debug/LogKitty-*-debug.apk` (Note: APK name configuration might need updating to `LogKitty`).

## CI/CD (GitHub Actions)
*   **CI (`android-ci.yml`, local):** unit tests + lint on every push/PR, then a debug build.
*   **Releases (central, HereLiesAz/workflows):** `play-publish.yml` is the entry point bound to the shared `android-release.yml`: every push/merge to `main` builds, signs and publishes to Google Play and a GitHub Release. See `docs/RELEASING.md`.

## Dependencies
*   **Kotlin:** 1.9.x / 2.0 (configured via catalog).
*   **Android Gradle Plugin:** 8.x.
*   **Jetpack Compose:** BOM-based versioning.
