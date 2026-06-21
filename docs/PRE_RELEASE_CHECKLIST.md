# Pre-release checklist

Run through this before publishing a LogKitty release. See **[RELEASING.md](RELEASING.md)** for the
build/signing/publish mechanics; this is the human checklist around them.

## One-time Google Play setup (first release only)
- [ ] Create a **Google Cloud service account** + JSON key; add it as the `PLAY_SERVICE_ACCOUNT_JSON`
      repo secret.
- [ ] In **Play Console → Users & permissions**, invite the service account and grant release access
      (testing tracks at minimum; production if you'll publish there).
- [ ] **Upload the first `.aab` manually** in the Play Console and complete the store listing,
      content rating, and **Data safety** form. The Play API can only publish to an app that already
      exists — automation can't do the first upload.
- [ ] Enrol in **Play App Signing** (recommended): your CI key becomes the upload key.

## Data safety / privacy (every release if it changed)
- [ ] Declare **AdMob / `AD_ID`** usage (data shared with Google for advertising) in the Data safety
      form; ensure the listing's privacy policy is current. See `PRIVACY_POLICY.md` / `PERMISSIONS.md`.
- [ ] Confirm the declared sensitive permissions still match the app: `READ_LOGS`,
      `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, `PACKAGE_USAGE_STATS`.

## Build verification (CI)
- [ ] `build-and-release` green on the release commit (compiles + bundles all modules).
- [ ] Do a **dry run** of `play-publish.yml` with `publish: off` and inspect the produced `.aab`
      artifact.

## On-device smoke test (CI can't cover these)
Install a release build — ideally from a **Play internal-testing** track, or a
`bundletool build-apks --local-testing` install — and verify:
- [ ] **Resource shrinking** didn't strip anything needed: the on-demand install dialogs show their
      titles (Developer Stats, Ads), and no screen is missing text/icons. (`isShrinkResources` is on;
      cross-module title strings are protected by `res/raw/keep.xml`.)
- [ ] **On-demand modules install and load**: open the Stats tab (downloads `:feature:stats`); the
      AdMob banner appears in Settings (downloads `:feature:ads`).
- [ ] **Context Mode** works: foreground-app auto-filtering after granting Usage Access (non-root),
      and via `dumpsys` on a rooted device (no grant needed).
- [ ] **Root vs non-root**: logcat via `su` (root) and via the ADB `READ_LOGS` grant (non-root); the
      app picker shows the full list on root and launchable apps otherwise.
- [ ] **In-app update**: with a higher `versionCode` on the track, launching the app offers the
      flexible update and, once downloaded, shows the "Restart & update" prompt.

## Versioning
- [ ] `versionCode` increases — CI derives it from `git rev-list --count HEAD`, so just make sure the
      release commit is ahead of the last published one.
