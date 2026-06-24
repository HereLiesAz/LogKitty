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
| `QUERY_ALL_PACKAGES` | sensitive (Play declaration) | Let the user pick **any** installed app to monitor, and classify each log line by its source app/category. | Installed-app list used on-device only; never transmitted. |
| `INTERNET` | normal | Download the chosen code fonts, show the Settings banner ad, and (in some builds) upload crash reports. | LogKitty never uploads your logs. Fonts/ads handled by Google SDKs; crash reports = stack trace + device metadata only. |
| `com.google.android.gms.permission.AD_ID` | normal (Play data-safety) | Used by the Google Mobile Ads SDK for the Settings banner. | Advertising ID handled by Google's ad SDK; user can opt out in device Google ad settings. |
| `BIND_ACCESSIBILITY_SERVICE` | declared on the service (system-bound) | Powers **Context Mode**: detects the foreground app and Home/Recents transitions to auto-filter the log and collapse the overlay. **Not** an assistive tool. | Foreground package name + window-state events only. `canRetrieveWindowContent=false` — no screen content, text, or input is read. Nothing leaves the device. |

---

## Google Play Console declarations (the four flagged items)

Play flagged four declaration cards. Paste-ready text for each below.

### 1. Accessibility service — declaration / `isAccessibilityTool`

**Status:** `android:isAccessibilityTool` is intentionally **not set (false)**.
LogKitty is **not** an assistive tool for users with disabilities, so claiming
otherwise would misrepresent the app. Because it uses the AccessibilityService
API for a non-assistive purpose, it instead meets the **prominent disclosure +
consent** requirement (see item 2).

**How the API is used (paste into the accessibility declaration):**
> LogKitty is a developer log (logcat) viewer with an optional "Context Mode."
> It uses the AccessibilityService API solely to read the package name of the
> foreground app and to detect Home/Recents transitions (TYPE_WINDOW_STATE_CHANGED
> events). This lets LogKitty automatically filter the log to the app the user is
> currently viewing and collapse its overlay when the user leaves an app. The
> service has `canRetrieveWindowContent=false`; it does not read screen content,
> text, form fields, or user input, and no data collected leaves the device.

### 2. Accessibility service — prominent disclosure + consent video

**In-app disclosure:** before Context Mode is enabled, LogKitty shows a
full-screen consent dialog (the "Enable Context Mode" popup) explaining the use,
its limited scope, and that no personal data is read; the user must tap
**Agree** before being sent to Accessibility settings.

**What the demo video must show (Play requirement):**
1. The app open on the LogKitty Settings screen.
2. The user toggling **Context Mode (Auto-Filter)** on.
3. The **prominent disclosure dialog** appearing, with its full text legible —
   stating that the Accessibility Service is used to detect the foreground app /
   Home & Recents transitions, only to auto-filter the log and collapse the
   overlay, and that no screen content or personal data is read.
4. The user tapping **Agree**, then enabling the service in system Accessibility
   settings.
Keep the video short (15–30s), unlisted/public link (YouTube/Drive), no edits
that hide the disclosure text.

### 3. `QUERY_ALL_PACKAGES` — core purpose

**Permitted-use answer (paste into "Describe 1 feature…"):**
> LogKitty is a developer log (logcat) viewer. Its core feature lets the user
> select any installed app to monitor that app's logs, and it labels every log
> line with the originating app and category. This requires broad visibility
> into installed apps to (a) present the app picker and (b) resolve arbitrary
> process UIDs from the log stream to package names for per-app filtering and
> source classification. The installed-app information is used only on-device
> and is never transmitted.

*(If Play rejects this use, the fallback is to drop `QUERY_ALL_PACKAGES` and
limit the picker to launchable apps via the manifest `<queries>` element, with
reduced source-classification accuracy.)*

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

- **Advertising ID (`AD_ID`):** declare advertising-ID collection/usage in the
  **Data safety** form (used for ads via the Google Mobile Ads SDK).
- **Usage access (`PACKAGE_USAGE_STATS`):** a special app access the user grants
  in system settings; surfaced and explained in-app under Settings → Permissions.
- **`app-ads.txt`:** host `app-ads.txt` (in the repo root) at your developer
  website root so AdMob can verify the authorized seller.

_Last updated: 2026-06-08._
