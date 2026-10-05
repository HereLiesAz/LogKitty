# Releasing LogKitty (Google Play, App Bundle)

LogKitty ships as an **Android App Bundle (`.aab`)** with **dynamic feature modules**, so Google
Play delivers only what each device/user needs. This doc covers building a signed bundle, the
versionCode scheme, the modules, and how to publish via CI.

## Module layout

| Module | Type | Play delivery | Why |
| --- | --- | --- | --- |
| `:app` | base application | always installed | overlay, logcat core, settings, app picker, Context Mode |
| `:core` | android library | (folded into base) | feature interfaces + reflective loader |
| `:feature:stats` | dynamic feature | **on-demand** | Developer Stats; defers `PACKAGE_USAGE_STATS` |


Each dynamic feature declares `<dist:delivery><dist:on-demand/></dist:delivery>` and
`<dist:fusing dist:include="true"/>`. Fusing means the **universal/standalone APK is a full
monolith** (used for the sideloaded GitHub release), while **Play** streams the modules on demand via
`SplitInstallManager`. The base app is installable on its own.

### Automatic configuration splits

App Bundles already produce per-device **density / ABI / language** splits automatically — there are
no separate artifacts to build. You upload one `.aab`; Play generates the optimized APKs.

## Versioning

`version.properties` follows the central **HereLiesAz/workflows** version contract:
`versionMajor`, `versionMinor`, `versionPatch` (hand-managed) and `versionBuild`. The central release
workflows also record the published `versionCode` / `versionName` there.

- **Central release builds** decide the published pair (Play's next free `versionCode`) and pass it
  as `-PversionCodeOverride` / `-PversionNameOverride` (plus `-PversionBuild`); those always win.
- **Local / Android Studio**: `versionCode = (major*10_000 + minor*100 + patch)*100_000 + build`,
  `versionName = major.minor.patch.build`, with `build` auto-incremented in the untracked
  `.local-build-number`.

## Build a signed AAB locally

Signing reads environment variables (same names CI uses):

```bash
export KEYSTORE_FILE=/abs/path/to/upload.keystore
export KEYSTORE_PASSWORD=…
export KEY_ALIAS=…
export KEY_PASSWORD=…

./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

> Never commit a keystore or secrets. There is no `keystore.properties` in this repo — signing is
> env-injected, and CI reconstructs the keystore from secrets at runtime.

The GitHub release APK is built by the central Android GitHub Release workflow (see below).

## Publish via CI (central workflows)

LogKitty uses the same build and publishing workflows as the other HereLiesAz Android apps. They run
in **HereLiesAz/workflows**; this repo keeps only the entry points, which the central sync turns into
trackers (one short job that points at the commit status carrying the central result):

| Entry point | Central implementation | What it does |
| --- | --- | --- |
| `.github/workflows/play-publish.yml` — "Publish to Google Play" | `android-play-release.yml` | Signed `bundleRelease`, R8 mapping upload, Play tracks chosen at dispatch (`track`, `status`, `publish`) |
| `.github/workflows/android-release-apk.yml` — "Compile and Release APK" | `android-github-release.yml` | Signed release APK published to the grouped GitHub Release |

LogKitty's profile (signing mode, tracks-from-inputs, app name) lives in the central
`scripts/semantic_catalog.py`. Run either from Actions → *Run workflow*; results appear as a commit
status named after the entry point's path.

`android-ci.yml` stays local by design (CI always runs in its own repository).

## Required repository secrets

**Signing** (used by the central release workflows):

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_PRIVATE` (or `KEYSTORE_RSA`) | PEM private key for the upload cert |
| `KEYSTORE_CHAIN` | PEM certificate chain |
| `KEYSTORE_PASSWORD` | keystore/store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | private-key password (if the key is encrypted) |

**Play publishing** (planned — no workflow uses this yet; upload the `.aab` manually):

| Secret | Purpose |
| --- | --- |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Cloud service-account JSON with Play release access |

## One-time Google Play setup

1. **Service account**: in Google Cloud, create a service account and a JSON key. Paste the JSON
   into the `PLAY_SERVICE_ACCOUNT_JSON` repo secret.
2. **Grant access**: in **Play Console → Users & permissions**, invite the service-account email and
   grant it release permissions (at least "Release to testing tracks"; add production if you intend
   to publish there).
3. **First upload is manual**: the Play Developer API can only publish to an app that **already
   exists**. For a brand-new app, **upload the first `.aab` by hand** in the Play Console (create the
   app, complete the store listing / content rating / data-safety form). After that, this workflow
   can publish subsequent builds via the API.
4. **App signing**: enrolling in **Play App Signing** is recommended — your CI key becomes the
   *upload* key and Google manages the release signing key.

## Data safety & privacy

- See `docs/PRIVACY_POLICY.md` and
  `docs/PERMISSIONS.md`.
- Because `PACKAGE_USAGE_STATS` and the accessibility capability are deferred into
  on-demand modules, they are only requested once the user pulls in the relevant feature — but they
  must still be disclosed where applicable, since the fused/Play app can request them.

## Optional follow-ups

- **Resource shrinking**: R8 minify is already enabled for release. `isShrinkResources = true` could
  shrink further, but verify first that it doesn't strip resources referenced **across module
  boundaries** (e.g. the `feature_*_title` strings and `accessibility_service_config` that dynamic
  feature manifests reference from the base) — confirm on a real release build before enabling.
- **Play in-app updates** (`com.google.android.play:app-update`): prompt users to update from within
  the app; a natural fit alongside this on-demand delivery setup.
