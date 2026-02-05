package com.hereliesaz.logkitty.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * [LogKittyAccessibilityService] is a lightweight Accessibility Service designed purely for
 * "Context Awareness".
 *
 * Its ONLY job is to detect which application is currently in the foreground (active on screen)
 * so that LogKitty can filter the log stream to show only relevant logs for that app.
 *
 * **Privacy Note:**
 * This service does NOT inspect UI content, read text, or track user inputs.
 * It only listens for [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] to get package names.
 */
class LogKittyAccessibilityService : AccessibilityService() {

    private val TAG = "LogKittyAccess"

    companion object {
        // Broadcast action sent when the foreground app changes.
        const val ACTION_FOREGROUND_APP_CHANGED = "com.hereliesaz.logkitty.FOREGROUND_APP_CHANGED"

        // Broadcast action sent when the user returns to the home screen (requesting the overlay to collapse).
        const val ACTION_COLLAPSE_OVERLAY = "com.hereliesaz.logkitty.COLLAPSE_OVERLAY"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }

    /**
     * Called back by the system when an accessibility event occurs.
     * We filter strictly for window state changes.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                // Broadcast the new foreground app to the MainViewModel (via a Receiver or direct observation)
                // Note: The ViewModel actually uses a receiver or monitors this indirectly.
                val intent = Intent(ACTION_FOREGROUND_APP_CHANGED).apply {
                    putExtra("PACKAGE_NAME", packageName)
                    // Explicitly set package to keep the broadcast internal/secure
                    setPackage(this@LogKittyAccessibilityService.packageName)
                }
                sendBroadcast(intent)

                // Smart Feature: Auto-Collapse
                // If the user goes to the Home Screen or opens Recents/App Switcher,
                // we assume they are done debugging for a moment and collapse the overlay to get out of the way.
                if (isSystemNavPackage(packageName)) {
                    val collapseIntent = Intent(ACTION_COLLAPSE_OVERLAY).apply {
                        setPackage(this@LogKittyAccessibilityService.packageName)
                    }
                    sendBroadcast(collapseIntent)
                }
            }
        }
    }

    /**
     * Checks if the package name belongs to known system navigation components (Launchers, SystemUI).
     */
    private fun isSystemNavPackage(pkg: String): Boolean {
        return pkg.contains("launcher", ignoreCase = true) || // Common pattern for 3rd party launchers
               pkg == "com.android.systemui" || // System UI (Recents, Notification Shade)
               pkg == "com.google.android.apps.nexuslauncher" || // Pixel Launcher
               pkg == "com.sec.android.app.launcher" // Samsung One UI Home
    }

    /**
     * Called when the system wants to interrupt the feedback. Not used here.
     */
    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }
}
