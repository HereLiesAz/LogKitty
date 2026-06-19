package com.hereliesaz.logkitty.feature.appmonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hereliesaz.logkitty.core.AccessibilityActions

/**
 * [LogKittyAccessibilityService] supplies *Context Awareness* (foreground-app detection) and
 * also signals when the user reaches the launcher or the recents screen so the overlay can
 * collapse out of the way.
 *
 * It ships in the on-demand `:feature:appmonitor` module, and communicates with the base app purely
 * through the broadcast actions/constants defined in [AccessibilityActions] (in `:core`) — the base
 * never references this class directly, since the module may not be installed.
 *
 * **Privacy Note:**
 * This service does NOT inspect UI content, read text, or track user inputs.
 * It only consumes [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] to read package names.
 */
class LogKittyAccessibilityService : AccessibilityService() {

    private val TAG = "LogKittyAccess"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        AccessibilityActions.resolvedLauncherPackage = try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return

        val foregroundIntent = Intent(AccessibilityActions.ACTION_FOREGROUND_APP_CHANGED).apply {
            putExtra(AccessibilityActions.EXTRA_PACKAGE_NAME, packageName)
            setPackage(this@LogKittyAccessibilityService.packageName)
        }
        sendBroadcast(foregroundIntent)

        val reason = systemTransitionReason(packageName, event.className?.toString())
        if (reason != null) {
            val collapseIntent = Intent(AccessibilityActions.ACTION_COLLAPSE_OVERLAY).apply {
                setPackage(this@LogKittyAccessibilityService.packageName)
                putExtra(AccessibilityActions.EXTRA_REASON, reason)
            }
            sendBroadcast(collapseIntent)
        }
    }

    /** Returns [AccessibilityActions.REASON_HOME], [AccessibilityActions.REASON_RECENTS], or null. */
    private fun systemTransitionReason(pkg: String, cls: String?): String? {
        val lowerCls = cls?.lowercase().orEmpty()
        val isRecents = lowerCls.contains("recents") ||
            lowerCls.contains("taskswitcher") ||
            lowerCls.contains("overview")
        return when {
            isRecents -> AccessibilityActions.REASON_RECENTS
            AccessibilityActions.isLauncherPackage(pkg) -> AccessibilityActions.REASON_HOME
            pkg == "com.android.systemui" -> AccessibilityActions.REASON_RECENTS
            else -> null
        }
    }

    override fun onInterrupt() { Log.d(TAG, "onInterrupt") }
}
