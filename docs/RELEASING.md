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
| `:feature:ads` | dynamic feature | **on-demand** | AdMob banner; defers `AD_ID` + the ad SDK |

Each dynamic feature declares `<dist:delivery><dist:on-demand/></dist:delivery>` and
`<dist:fusing dist:include="true"/>`. Fusing means the **universal/standalone APK is a full
monolith** (used for the sideloaded GitHub release), while **Play** streams the modules on demand via
`SplitInstallManager`. The base app is installable on its own.

### Automatic configuration splits

App Bundles already produce per-device **density / ABI / language** splits automatically — there are
no separate artifacts to build. You upload one `.aab`; Play generates the optimized APKs.

## Versioning

- `versionName` = `major.minor.patch.build` (from `version.properties`).
- `versionCode` = `(major*10_000 + minor*100 + patch)*100_000 + buildNumber` — `buildNumber` gets
  its own 5-digit slot so a commit-count value (up to 99,999) never overflows into the semver digits
  (envelope: `major` ≤ 2, `minor`/`patch` ≤ 99; stays under Android's 2,100,000,000 cap).
- **`buildNumber` source:**
  - **CI / Play**: pass `-PversionBuild=<n>`; we use `git rev-list --count HEAD`. The commit count
    only ever grows, so every upload gets a strictly-increasing `versionCode` (Play rejects
    duplicate or lower codes). `version.properties` is left untouched in this mode.
  - **Local / Android Studio** (no override): `buildNumber` auto-increments in `version.properties`
    on each build task — unchanged from before.

## Build a signed AAB locally

Signing reads environment variables (same names CI uses):

```bash
export KEYSTORE_FILE=/abs/path/to/upload.keystore
export KEYSTORE_PASSWORD=…
export KEY_ALIAS=…
export KEY_PASSWORD=…

# versionCode from commit count (matches CI); omit -PversionBuild for the local auto-increment.
./gradlew bundleRelease -PversionBuild=$(git rev-list --count HEAD)
# Output: app/build/outputs/bundle/release/app-release.aab
```

> Never commit a keystore or secrets. There is no `keystore.properties` in this repo — signing is
> env-injected, and CI reconstructs the keystore from secrets at runtime.

To produce a single installable APK from the bundle (what the GitHub release uses), see the
`build-and-release.yml` workflow's bundletool `--mode=universal` step.

## Publish via CI

Workflow: **`.github/workflows/play-publish.yml`** — `workflow_dispatch` (Actions → "Publish to
Google Play" → Run workflow). Inputs:

| Input | Default | Meaning |
| --- | --- | --- |
| `track` | `internal` | `internal` / `alpha` / `beta` / `production` |
| `status` | `draft` | `draft` (review in console before going live) / `completed` |
| `publish` | `false` | **off = build + upload the `.aab` as a CI artifact only** (dry run, never touches Play) |

It builds a signed `bundleRelease` with the commit-count versionCode, uploads the `.aab` artifact,
and — only when `publish` is on — pushes to Play with `r0adkll/upload-google-play@v1` (including the
R8 `mapping.txt` for crash deobfuscation).

`build-and-release.yml` is unchanged in purpose: it still produces the `.aab` + a fused universal APK
for the **GitHub** release channel.

## Required repository secrets

**Signing** (already used by `build-and-release.yml`):

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_PRIVATE` (or `KEYSTORE_RSA`) | PEM private key for the upload cert |
| `KEYSTORE_CHAIN` | PEM certificate chain |
| `KEYSTORE_PASSWORD` | keystore/store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | private-key password (if the key is encrypted) |

**Play publishing** (new):

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

- The app integrates **AdMob** (`:feature:ads`), which uses the **`AD_ID`** advertising identifier
  and sends data to Google. The Play **Data safety** form must declare this (data shared with third
  parties for advertising), and the listing needs a privacy policy. See `docs/PRIVACY_POLICY.md` and
  `docs/PERMISSIONS.md`.
- Because `AD_ID`, `PACKAGE_USAGE_STATS`, and the accessibility capability are deferred into
  on-demand modules, they are only requested once the user pulls in the relevant feature — but they
  must still be disclosed where applicable, since the fused/Play app can request them.

## Optional follow-ups

- **Resource shrinking**: R8 minify is already enabled for release. `isShrinkResources = true` could
  shrink further, but verify first that it doesn't strip resources referenced **across module
  boundaries** (e.g. the `feature_*_title` strings and `accessibility_service_config` that dynamic
  feature manifests reference from the base) — confirm on a real release build before enabling.
- **Play in-app updates** (`com.google.android.play:app-update`): prompt users to update from within
  the app; a natural fit alongside this on-demand delivery setup.
