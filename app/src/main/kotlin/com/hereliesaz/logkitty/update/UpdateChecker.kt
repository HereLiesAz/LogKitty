package com.hereliesaz.logkitty.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.hereliesaz.logkitty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Finds the update that applies to *this* install and where to get it.
 *
 * - **Installed by Google Play** → asks Play (in-app update API). Play builds must update through
 *   Play: a GitHub APK carries a different signature and wouldn't install over it.
 * - **Anything else** (sideloaded GitHub APK, ADB) → asks the GitHub Releases API. Release builds
 *   compare against the latest stable release (`vX.Y.Z.B`); debug builds against the newest
 *   `latest-debug-vX.Y` prerelease.
 */
object UpdateChecker {

    enum class Source { PLAY, GITHUB }

    sealed interface Result {
        data object UpToDate : Result
        data class Failed(val source: Source) : Result
        /** [version] is null when the store doesn't say (Play only reports a version code). */
        data class Available(val source: Source, val version: String?, val url: String) : Result
    }

    private const val REPO = "HereLiesAz/LogKitty"
    private const val PLAY_PACKAGE = "com.android.vending"
    private const val TIMEOUT_MS = 10_000

    fun source(context: Context): Source = if (installer(context) == PLAY_PACKAGE) Source.PLAY else Source.GITHUB

    suspend fun check(context: Context): Result = when (source(context)) {
        Source.PLAY -> checkPlay(context)
        Source.GITHUB -> checkGitHub()
    }

    /** Opens the place the update lives: the Play listing or the GitHub release page. */
    fun open(context: Context, update: Result.Available) {
        val intents = when (update.source) {
            Source.PLAY -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).setPackage(PLAY_PACKAGE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")),
            )
            Source.GITHUB -> listOf(Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
        }
        for (intent in intents) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    private fun installer(context: Context): String? = runCatching {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    }.getOrNull()

    private suspend fun checkPlay(context: Context): Result {
        val info = withTimeoutOrNull(TIMEOUT_MS.toLong()) {
            suspendCancellableCoroutine<AppUpdateInfo?> { cont ->
                AppUpdateManagerFactory.create(context).appUpdateInfo
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        } ?: return Result.Failed(Source.PLAY)
        return if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        ) Result.Available(Source.PLAY, null, "https://play.google.com/store/apps/details?id=${context.packageName}")
        else Result.UpToDate
    }

    private suspend fun checkGitHub(): Result = withContext(Dispatchers.IO) {
        val body = runCatching { get("https://api.github.com/repos/$REPO/releases?per_page=30") }.getOrNull()
            ?: return@withContext Result.Failed(Source.GITHUB)
        newestRelease(body, BuildConfig.VERSION_NAME, debug = BuildConfig.DEBUG) ?: Result.UpToDate
    }

    /**
     * From a GitHub `releases` JSON array, the release newer than [current], or null. Stable builds
     * consider non-prerelease `vX.Y.Z[.B]` tags; debug builds consider `latest-debug-vX.Y` tags.
     */
    internal fun newestRelease(json: String, current: String, debug: Boolean): Result.Available? {
        val releases = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return null
        var best: Pair<List<Int>, Result.Available>? = null
        for (element in releases) {
            val obj = element.jsonObject
            if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) continue
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: continue
            val url = obj["html_url"]?.jsonPrimitive?.content ?: continue
            val prerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true
            val version = when {
                debug && tag.startsWith(DEBUG_PREFIX) -> tag.removePrefix(DEBUG_PREFIX)
                !debug && !prerelease && STABLE_TAG.matches(tag) -> tag.removePrefix("v")
                else -> continue
            }
            val parts = parse(version) ?: continue
            if (best == null || compare(parts, best.first) > 0) best = parts to Result.Available(Source.GITHUB, version, url)
        }
        val (parts, available) = best ?: return null
        val mine = parse(current) ?: return null
        // A debug tag names only major.minor, so compare on the parts it has.
        return available.takeIf { compare(parts, mine.take(parts.size)) > 0 }
    }

    private const val DEBUG_PREFIX = "latest-debug-v"
    private val STABLE_TAG = Regex("""^v\d+(\.\d+)*$""")

    private fun parse(version: String): List<Int>? =
        version.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "LogKitty/${BuildConfig.VERSION_NAME}")
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
