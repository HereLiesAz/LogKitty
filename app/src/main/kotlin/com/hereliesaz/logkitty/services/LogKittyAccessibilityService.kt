package com.hereliesaz.logkitty.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * A focused Accessibility Service.
 * Its ONLY job is to detect which app is currently in the foreground
 * to enable "Contextual Logging".
 *
 * No more UI inspection. No more tap detection.
 */
class LogKittyAccessibilityService : AccessibilityService() {

    private val TAG = "LogKittyAccess"

    companion object {
        const val ACTION_FOREGROUND_APP_CHANGED = "com.hereliesaz.logkitty.FOREGROUND_APP_CHANGED"
        const val ACTION_COLLAPSE_OVERLAY = "com.hereliesaz.logkitty.COLLAPSE_OVERLAY"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                // Broadcast the new foreground app
                val intent = Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                    putExtra("PACKAGE_NAME", packageName)
                    setPackage(this@LogKittyAccessibilityService.packageName)
                }
                sendBroadcast(intent)

                // Check for Home or Recents to collapse overlay automatically
                if (isSystemNavPackage(packageName)) {
                    val collapseIntent = Intent(ACTION_COLLAPSE_OVERLAY).apply {
                        setPackage(this@LogKittyAccessibilityService.packageName)
                    }
                    sendBroadcast(collapseIntent)
                }
            }
        }
    }

    private fun isSystemNavPackage(pkg: String): Boolean {
        return pkg.contains("launcher", ignoreCase = true) || // Common launcher
               pkg == "com.android.systemui" || // System UI (Recents, Notification Shade)
               pkg == "com.google.android.apps.nexuslauncher" || // Pixel Launcher
               pkg == "com.sec.android.app.launcher" // Samsung One UI Home
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }
}
