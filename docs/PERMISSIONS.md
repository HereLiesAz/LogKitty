---
title: Permissions
permalink: /permissions/
---

# Permissions

This document explains every permission LogKitty declares, why it's needed, and
what data (if any) it touches. The same explanations are surfaced in-app under
**Settings → Permissions** (tap a permission for a popup). A second section
gives the **paste-ready Google Play Console declaration text** for the four
items Play flagged.

LogKitty is an on-device developer log (logcat) viewer. It has no account
system, no analytics, and no server of its own — your logs are read and shown
locally and are never uploaded by the app. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

---

## All permissions

| Permission | Type | Why LogKitty needs it | Data handling |
| --- | --- | --- | --- |
| `READ_LOGS` | signature / privileged (ADB or root) | The core feature: read the system log (logcat) so it can be displayed. | Read on-device, shown in the overlay; never uploaded by the app. |
| `SYSTEM_ALERT_WINDOW` | special (user grant) | Draw the floating log overlay on top of other apps so you can read logs while using the app being debugged. | None. |
| `FOREGROUND_SERVICE` | normal | Run the log-capture/overlay as a foreground service so it keeps working while you use other apps. | None. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | special (Play declaration) | Declares the foreground service's "special use": a persistent on-screen log overlay for real-time debugging. | None. |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | Show the persistent, silent notification that lets you start/stop capture and confirms the service is running. | None. |
| `PACKAGE_USAGE_STATS` | special app access | Let Context Mode detect the foreground app (where available) to auto-filter the log to it. | Foreground package name, processed on-device only. |
| `INTERNET` | normal | Download the chosen code fonts, talk to Google Play for updates. | LogKitty never uploads your logs. Fonts handled by Google SDKs. No advertising ID is read or transmitted. |

---

## Google Play Console declarations (the four flagged items)

Play flagged four declaration cards. Paste-ready text for each below.

> **No longer applicable:** the accessibility service and `QUERY_ALL_PACKAGES` were removed.
> Context Mode detects the foreground app with `PACKAGE_USAGE_STATS` (or `dumpsys` under root), and
> the app picker uses the manifest's launcher `<queries>`. Withdraw those Play declarations if they
> are still on file.

### 4. `FOREGROUND_SERVICE_SPECIAL_USE`

**Manifest property (already present):**
```xml
<property
    android:name="android.app.property.FOREGROUND_SERVICE_TYPE_SPECIAL_USE_DESCRIPTION"
    android:value="Used to display a persistent logcat overlay for real-time application debugging." />
```

**Declaration answer (paste into the special-use justification):**
> LogKitty displays a persistent floating log (logcat) overlay for real-time
> debugging. The foreground service keeps the overlay and log capture active —
> with an ongoing, user-dismissable notification — while the user interacts with
> other apps being debugged. This is a continuously user-noticeable task (a
> visible on-screen overlay + persistent notification) that must keep running in
> the background, and no narrower foreground-service type (camera, location,
> media playback, data sync, etc.) describes an on-screen debugging overlay.

---

## Other Play data declarations to remember

- **Advertising ID (`AD_ID`):** LogKitty **does not use** the advertising ID.
  Ads and the Google Mobile Ads SDK were removed, and every module manifest —
  the base plus each dynamic feature — strips the permission with
  `tools:node="remove"` so no transitive dependency can merge it back in. Each
  dynamic feature runs its own manifest merge, so the base module's removal does
  not cover them; the declaration has to be repeated per module. Play still
  requires an answer:
  - **App content → Advertising ID** → answer **"No, my app does not use
    advertising ID"**. Leaving this unanswered is what produces the
    *"Incomplete advertising ID declaration"* blocker.
  - **Data safety** → do not list advertising ID under collected/shared data.
  - Play cross-checks the answer against the uploaded bundle: answering "No"
    while the bundle still contains `com.google.android.gms.permission.AD_ID`
    is rejected. The *Publish to Google Play* workflow gates the upload on this,
    running `bundletool dump manifest` per bundle module before it touches Play.
    To check by hand, do the same thing — the plain
    `bundletool dump manifest --bundle=app-release.aab` only reads the **base**
    module, so pass `--module=<name>` for each feature too.

  **If Play rejects the edit but the bundle verifies clean.** The API returns the
  same *"This release includes the com.google.android.gms.permission.AD_ID
  permission…"* message when the permission comes from an older version code
  that is still **active on the target track**, not from the bundle being
  uploaded. Nothing in this repository can fix that. In the Play Console:
  1. **App bundle explorer** → check **Permissions** per active version code to
     find which ones carry `AD_ID`. Anything built before commit `0b71c7b`
     (which removed the `:feature:ads` module) is suspect.
  2. Either deactivate those releases on every track, **or** flip the
     declaration to "Yes", roll a verified-clean build out to every track
     including production, then flip it back to "No".
- **Usage access (`PACKAGE_USAGE_STATS`):** a special app access the user grants
  in system settings; surfaced and explained in-app under Settings → Permissions.

_Last updated: 2026-07-25._
