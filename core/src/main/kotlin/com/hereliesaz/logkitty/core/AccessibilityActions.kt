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
    const val ACTION_COLLAPSE_OVERLAY = "com.hereliesaz.logkitty.COLLAPSE_OVERLAY"

    const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"
    const val EXTRA_REASON = "reason"
    const val REASON_HOME = "home"
    const val REASON_RECENTS = "recents"

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
}
