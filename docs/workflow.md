# Workflow

## Git
*   **Main Branch:** `main`.
*   **Feature Branches:** `feature/xyz`, `fix/abc`.
*   **Commit Messages:** Conventional Commits (`feat: ...`, `fix: ...`).

## Release
1.  Bump `versionMajor`/`versionMinor`/`versionPatch` in `version.properties` when warranted. `versionBuild`/`versionCode` are managed by the central release workflows; locally the build counter lives in the untracked `.local-build-number`.
2.  Commit.
3.  Tag (optional).
4.  Run **Publish to Google Play** and/or **Compile and Release APK** from Actions (central workflows; see `RELEASING.md`).
