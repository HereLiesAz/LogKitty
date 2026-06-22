package com.hereliesaz.logkitty.core.feature

/**
 * Names of the dynamic feature modules and the fully-qualified entry-point classes the base loads
 * reflectively once a module is installed. Keep these in sync with each module's `dist:module`
 * name and its implementation class.
 */
object FeatureModules {
    const val STATS = "stats"
    const val ADS = "ads"
    const val GITHUB = "github"

    const val STATS_IMPL = "com.hereliesaz.logkitty.feature.stats.StatsFeatureImpl"
    const val ADS_IMPL = "com.hereliesaz.logkitty.feature.ads.AdsFeatureImpl"
    const val GITHUB_IMPL = "com.hereliesaz.logkitty.feature.github.GitHubFeatureImpl"
}

/**
 * Instantiates a dynamic feature module's entry-point class by name.
 *
 * Returns `null` (rather than throwing) when the class isn't present yet — i.e. the module hasn't
 * been installed, or split resources haven't been wired into the current context. Callers treat a
 * `null` as "feature unavailable" and fall back to an install prompt.
 *
 * Resolves through [context]'s class loader: after a SplitCompat install that loader is the one
 * that gains the freshly-installed split's classes, so it's more reliable than `Class.forName`'s
 * implicit caller class loader (which may not have been patched by SplitCompat).
 */
object FeatureLoader {
    @Suppress("UNCHECKED_CAST")
    fun <T> load(className: String, context: android.content.Context): T? = try {
        context.classLoader.loadClass(className).getDeclaredConstructor().newInstance() as T
    } catch (e: Throwable) {
        null
    }
}
