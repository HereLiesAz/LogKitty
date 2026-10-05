package com.hereliesaz.logkitty.core

/**
 * Broadcast actions and launcher detection for Context Mode (foreground-app awareness).
 *
 * The foreground app is detected by [com.hereliesaz.logkitty.services] (UsageStatsManager on
 * non-root devices, `dumpsys` on rooted ones — no accessibility service required) and announced via
 * [ACTION_FOREGROUND_APP_CHANGED]; the ViewModel and overlay react to it. The constants live in
 * `:core` so producers and consumers across modules agree on them.
 */
object AccessibilityActions {
    const val ACTION_FOREGROUND_APP_CHANGED = "com.hereliesaz.logkitty.FOREGROUND_APP_CHANGED"

    const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"

    /** Resolved launcher package, cached by the foreground monitor so home is detected reliably. */
    @Volatile
    var resolvedLauncherPackage: String? = null

    fun isLauncherPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        resolvedLauncherPackage?.let { return pkg == it }
        return pkg == "com.google.android.apps.nexuslauncher" ||
            pkg == "com.sec.android.app.launcher" ||
            pkg == "com.android.launcher" ||
            pkg == "com.android.launcher3" ||
            pkg.contains("launcher", ignoreCase = true)
    }

    /**
     * Packages that take the foreground *between* apps rather than being the app under inspection:
     * the launcher, System UI, the `android` system package (crash / ANR dialogs), and LogKitty
     * itself ([ownPackage]). Context Mode stays locked on the last real app while one of these is
     * on top — otherwise a crash (crash dialog → home) or opening LogKitty to read the log would
     * retarget the stream and drop / hide the crashed app's lines.
     */
    fun isTransitPackage(pkg: String?, ownPackage: String?): Boolean {
        if (pkg.isNullOrBlank()) return true
        return pkg == ownPackage ||
            pkg == "android" ||
            pkg == "com.android.systemui" ||
            isLauncherPackage(pkg)
    }
}
