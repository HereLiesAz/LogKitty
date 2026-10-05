package com.hereliesaz.logkitty.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitPackageTest {
    private val own = "com.hereliesaz.logkitty"

    @Test fun transitPackagesKeepContext() {
        listOf(null, "", own, "android", "com.android.systemui", "com.android.launcher3")
            .forEach { assertTrue(it.toString(), AccessibilityActions.isTransitPackage(it, own)) }
    }

    @Test fun realAppsChangeContext() {
        assertFalse(AccessibilityActions.isTransitPackage("com.example.app", own))
        assertFalse(AccessibilityActions.isTransitPackage("com.android.chrome", own))
    }
}
