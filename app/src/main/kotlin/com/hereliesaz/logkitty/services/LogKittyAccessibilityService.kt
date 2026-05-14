package com.hereliesaz.logkitty.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * [LogKittyAccessibilityService] supplies *Context Awareness* (foreground-app detection) and
 * also signals when the user reaches the launcher or the recents screen so the overlay can
 * collapse out of the way.
 *
 * **Privacy Note:**
 * This service does NOT inspect UI content, read text, or track user inputs.
 * It only consumes [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] to read package names.
 */
class LogKittyAccessibilityService : AccessibilityService() {

    private val TAG = "LogKittyAccess"

    companion object {
        const val ACTION_FOREGROUND_APP_CHANGED = "com.hereliesaz.logkitty.FOREGROUND_APP_CHANGED"
        const val ACTION_COLLAPSE_OVERLAY = "com.hereliesaz.logkitty.COLLAPSE_OVERLAY"

        const val EXTRA_REASON = "reason"
        const val REASON_HOME = "home"
        const val REASON_RECENTS = "recents"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return

        val foregroundIntent = Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
            putExtra("PACKAGE_NAME", packageName)
            setPackage(this@LogKittyAccessibilityService.packageName)
        }
        sendBroadcast(foregroundIntent)

        val reason = systemTransitionReason(packageName, event.className?.toString())
        if (reason != null) {
            val collapseIntent = Intent(ACTION_COLLAPSE_OVERLAY).apply {
                setPackage(this@LogKittyAccessibilityService.packageName)
                putExtra(EXTRA_REASON, reason)
            }
            sendBroadcast(collapseIntent)
        }
    }

    /** Returns [REASON_HOME], [REASON_RECENTS], or null when the event is irrelevant. */
    private fun systemTransitionReason(pkg: String, cls: String?): String? {
        val lowerCls = cls?.lowercase().orEmpty()
        val isRecents = lowerCls.contains("recents") ||
            lowerCls.contains("taskswitcher") ||
            lowerCls.contains("overview")
        val isLauncher = pkg.contains("launcher", ignoreCase = true) ||
            pkg == "com.google.android.apps.nexuslauncher" ||
            pkg == "com.sec.android.app.launcher" ||
            pkg == "com.android.launcher" ||
            pkg == "com.android.launcher3"
        return when {
            isRecents -> REASON_RECENTS
            isLauncher -> REASON_HOME
            pkg == "com.android.systemui" -> REASON_RECENTS
            else -> null
        }
    }

    override fun onInterrupt() { Log.d(TAG, "onInterrupt") }
}
