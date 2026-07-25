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
      titles (Developer Stats, GitHub), and no screen is missing text/icons. (`isShrinkResources` is
      on; cross-module title strings are protected by `res/raw/keep.xml`.) Note this now runs in
      **optimized** mode (`android.r8.optimizedResourceShrinking=true` in `gradle.properties`),
      which additionally collapses duplicate resources and strips resource *names* from
      `resources.arsc` — so anything resolved by name at runtime via `Resources.getIdentifier`
      would fail here rather than at compile time. Worth a pass over the dynamic-feature install
      dialogs specifically, since those titles cross a module boundary.
- [ ] **Edge-to-edge safe areas** (enforced from Android 15; we opt in explicitly via
      `enableEdgeToEdge()` so API 30+ behaves the same). On a device with **gesture navigation** and
      again with **3-button navigation**, check that nothing is hidden or unreachable behind the
      bars:
  - [ ] Dashboard / permission wizard: the logo clears the status bar and the bottom
        "Settings" / "Open GitHub" buttons clear the navigation bar and gesture handle.
  - [ ] Scaffold screens (Settings, GitHub, Info, Color scheme editor, Prohibited logs): the top app
        bar sits below the status bar and the last list row scrolls clear of the navigation bar.
  - [ ] Dashboard FAB ("Start/Stop") is fully tappable, not clipped by the gesture handle.
  - [ ] Rotate to **landscape** and re-check — the display cutout moves to a side edge.
  - [ ] Settings text fields: the IME doesn't cover the focused field.
  - [ ] The overlay window is a separate `TYPE_APPLICATION_OVERLAY` window that measures the nav-bar
        inset itself, so confirm it is unchanged (peek strip still sits above the bar).
- [ ] **On-demand modules install and load**: open the Stats tab (downloads `:feature:stats`); the
- [ ] **Context Mode** works: foreground-app auto-filtering after granting Usage Access (non-root),
      and via `dumpsys` on a rooted device (no grant needed).
- [ ] **Root vs non-root**: logcat via `su` (root) and via the ADB `READ_LOGS` grant (non-root); the
      app picker shows the full list on root and launchable apps otherwise.
- [ ] **In-app update**: with a higher `versionCode` on the track, launching the app offers the
      flexible update and, once downloaded, shows the "Restart & update" prompt.

## Versioning
- [ ] `versionCode` increases — CI derives it from `git rev-list --count HEAD`, so just make sure the
      release commit is ahead of the last published one.
