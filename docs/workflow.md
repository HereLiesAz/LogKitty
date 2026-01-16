# Workflow

## Git
*   **Main Branch:** `main`.
*   **Feature Branches:** `feature/xyz`, `fix/abc`.
*   **Commit Messages:** Conventional Commits (`feat: ...`, `fix: ...`).

## Release
1.  Bump version in `version.properties`.
2.  Commit.
3.  Tag (optional).
4.  Build APK: `./gradlew assembleRelease`.
