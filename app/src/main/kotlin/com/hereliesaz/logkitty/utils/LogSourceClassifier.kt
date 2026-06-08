package com.hereliesaz.logkitty.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * Catalog of log-source filter keys plus their display labels and grouping, shared by the settings
 * UI and the ViewModel. A "source" is derived from the UID that emitted a log line.
 */
object LogSources {
    // Source buckets.
    const val USER = "USER"
    const val SYSTEM = "SYSTEM"
    const val PLAY_STORE = "PLAY_STORE"
    const val NON_PLAY_STORE = "NON_PLAY_STORE"

    // App-category buckets (from ApplicationInfo.category).
    const val CAT_GAME = "CAT_GAME"
    const val CAT_AUDIO = "CAT_AUDIO"
    const val CAT_VIDEO = "CAT_VIDEO"
    const val CAT_IMAGE = "CAT_IMAGE"
    const val CAT_SOCIAL = "CAT_SOCIAL"
    const val CAT_NEWS = "CAT_NEWS"
    const val CAT_MAPS = "CAT_MAPS"
    const val CAT_PRODUCTIVITY = "CAT_PRODUCTIVITY"
    const val CAT_ACCESSIBILITY = "CAT_ACCESSIBILITY"
    const val CAT_OTHER = "CAT_OTHER"

    /** Ordered bucket keys shown under the "Sources" header in settings. */
    val SOURCE_BUCKETS = listOf(USER, SYSTEM, PLAY_STORE, NON_PLAY_STORE)

    /** Ordered category keys shown under the "Categories" header in settings. */
    val CATEGORY_BUCKETS = listOf(
        CAT_GAME, CAT_AUDIO, CAT_VIDEO, CAT_IMAGE, CAT_SOCIAL,
        CAT_NEWS, CAT_MAPS, CAT_PRODUCTIVITY, CAT_ACCESSIBILITY, CAT_OTHER,
    )

    /** Human-readable label for a filter key (also used as the tab title). */
    fun label(key: String): String = when (key) {
        USER -> "User apps"
        SYSTEM -> "System"
        PLAY_STORE -> "Play Store"
        NON_PLAY_STORE -> "Non-Play Store"
        CAT_GAME -> "Games"
        CAT_AUDIO -> "Audio"
        CAT_VIDEO -> "Video"
        CAT_IMAGE -> "Images"
        CAT_SOCIAL -> "Social"
        CAT_NEWS -> "News"
        CAT_MAPS -> "Maps & Navigation"
        CAT_PRODUCTIVITY -> "Productivity"
        CAT_ACCESSIBILITY -> "Accessibility"
        CAT_OTHER -> "Uncategorized"
        else -> key
    }
}

/**
 * Classifies a log line's emitting UID into a set of [LogSources] keys (system/user, install
 * source, app category). Used to power the per-source log tabs.
 *
 * Results are cached per UID. Resolution requires the UID column on the log stream
 * (root / ADB `READ_LOGS`); lines without a UID can't be classified and only appear in the All tab.
 */
class LogSourceClassifier(context: Context) {

    private val pm: PackageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<Int, Set<String>>()

    /** Returns the set of source/category keys the [uid] belongs to (empty if unknown/null). */
    fun classify(uid: Int?): Set<String> {
        if (uid == null) return emptySet()
        cache[uid]?.let { return it }
        val result = compute(uid)
        cache[uid] = result
        return result
    }

    private fun compute(uid: Int): Set<String> {
        val out = mutableSetOf<String>()
        if (uid < Process.FIRST_APPLICATION_UID) out += LogSources.SYSTEM else out += LogSources.USER

        val pkgs = runCatching { pm.getPackagesForUid(uid) }.getOrNull()?.filterNotNull() ?: emptyList()
        for (pkg in pkgs) {
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            ) out += LogSources.SYSTEM

            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(pkg).installingPackageName
                } else {
                    @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                }
            }.getOrNull()
            if (installer == PLAY_STORE_PACKAGE) out += LogSources.PLAY_STORE else out += LogSources.NON_PLAY_STORE

            out += categoryKey(ai.category)
        }
        return out
    }

    private fun categoryKey(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> LogSources.CAT_GAME
        ApplicationInfo.CATEGORY_AUDIO -> LogSources.CAT_AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> LogSources.CAT_VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> LogSources.CAT_IMAGE
        ApplicationInfo.CATEGORY_SOCIAL -> LogSources.CAT_SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> LogSources.CAT_NEWS
        ApplicationInfo.CATEGORY_MAPS -> LogSources.CAT_MAPS
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> LogSources.CAT_PRODUCTIVITY
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> LogSources.CAT_ACCESSIBILITY
        else -> LogSources.CAT_OTHER
    }

    companion object {
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
