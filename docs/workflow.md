# Workflow

## Git
*   **Main Branch:** `main`.
*   **Feature Branches:** `feature/xyz`, `fix/abc`.
*   **Commit Messages:** Conventional Commits (`feat: ...`, `fix: ...`).

## Release
1.  Bump `major`/`minor`/`patch` in `version.properties` when warranted. The build number is automatic (commit count in CI; untracked `.local-build-number` locally) — don't edit it.
2.  Commit.
3.  Tag (optional).
4.  Build APK: `./gradlew assembleRelease`.
