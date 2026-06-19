package com.hereliesaz.logkitty.core

/**
 * Broadcast actions and launcher detection shared between the base app and the (optional) app-monitor
 * feature module that hosts the accessibility service.
 *
 * These live in `:core` so the base can keep registering/handling the broadcasts and collapsing the
 * overlay even when the accessibility service class itself ships in a dynamic module that may not be
 * installed.
 */
object AccessibilityActions {
    const val ACTION_FOREGROUND_APP_CHANGED = "com.hereliesaz.logkitty.FOREGROUND_APP_CHANGED"
    const val ACTION_COLLAPSE_OVERLAY = "com.hereliesaz.logkitty.COLLAPSE_OVERLAY"

    const val EXTRA_PACKAGE_NAME = "PACKAGE_NAME"
    const val EXTRA_REASON = "reason"
    const val REASON_HOME = "home"
    const val REASON_RECENTS = "recents"

    /**
     * Fully-qualified name of the accessibility service, which ships in the optional
     * `:feature:appmonitor` module. The base can't reference the class directly (it may not be
     * installed), so it compares against this string when checking whether the service is enabled.
     */
    const val SERVICE_CLASS_NAME = "com.hereliesaz.logkitty.feature.appmonitor.LogKittyAccessibilityService"

    /** Set by the accessibility service (when installed) to the device's resolved launcher package. */
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
