package com.hereliesaz.logkitty.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Iterates up through [ContextWrapper]s to find the nearest [Activity].
 * Returns null if no Activity host is found (e.g. when called from a Service context).
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
