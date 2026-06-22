package com.hereliesaz.logkitty.feature.github

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject

/**
 * Minimal GitHub Actions REST client. OkHttp arrives transitively from `:app` (already a base dep);
 * JSON is parsed with `org.json` so the module needs no serialization plugin.
 *
 * The PAT is read lazily via [tokenProvider] on each call (so a token edited in Settings is picked
 * up immediately and the plaintext isn't retained). All calls are `suspend` + run on [Dispatchers.IO].
 */
class GitHubApi(private val tokenProvider: () -> String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun listRuns(owner: String, repo: String, perPage: Int = 20): GitHubResult<List<WorkflowRun>> =
        get("$API/repos/$owner/$repo/actions/runs?per_page=$perPage") { body ->
            val arr = JSONObject(body.string()).optJSONArray("workflow_runs") ?: return@get emptyList()
            buildList {
                for (i in 0 until arr.length()) add(parseRun(arr.getJSONObject(i)))
            }
        }

    suspend fun listJobs(owner: String, repo: String, runId: Long): GitHubResult<List<WorkflowJob>> =
        get("$API/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100") { body ->
            val arr = JSONObject(body.string()).optJSONArray("jobs") ?: return@get emptyList()
            buildList {
                for (i in 0 until arr.length()) add(parseJob(arr.getJSONObject(i)))
            }
        }

    /**
     * Fetches a job's plain-text log as lines. The endpoint 302-redirects to a short-lived signed URL
     * on another host; OkHttp follows it and drops the `Authorization` header on the cross-host hop
     * (correct — re-sending it would make the signed fetch fail), so we must NOT add a global auth
     * interceptor.
     *
     * Logs can be tens of MB, so we **stream** the body line-by-line and keep only the most recent
     * [MAX_LOG_LINES] — never materializing the whole log as one String (avoids OOM). ANSI escapes
     * are left intact for the colored view to parse.
     */
    suspend fun fetchJobLog(owner: String, repo: String, jobId: Long): GitHubResult<List<String>> =
        get("$API/repos/$owner/$repo/actions/jobs/$jobId/logs") { body ->
            body.charStream().useLines { lines -> lines.toList().let { if (it.size > MAX_LOG_LINES) it.takeLast(MAX_LOG_LINES) else it } }
        }

    private suspend fun <T> get(url: String, parse: (ResponseBody) -> T): GitHubResult<T> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body ?: return@use GitHubResult.Err("Empty response from GitHub.")
                            GitHubResult.Ok(parse(body))
                        }
                        resp.code == 401 || resp.code == 403 ->
                            GitHubResult.Err("Unauthorized — check your token and its scopes.", unauthorized = true)
                        resp.code == 404 ->
                            GitHubResult.Err("Not found — check the owner/repo (and token access for private repos).")
                        else -> GitHubResult.Err("GitHub error ${resp.code}.")
                    }
                }
            } catch (e: Exception) {
                GitHubResult.Err(e.message ?: "Network error.")
            }
        }

    private fun parseRun(o: JSONObject) = WorkflowRun(
        id = o.optLong("id"),
        name = o.optStringSafe("name").ifBlank { o.optStringSafe("display_title").ifBlank { "Workflow run" } },
        status = o.optStringSafe("status"),
        conclusion = o.optStringSafe("conclusion").takeIf { it.isNotBlank() },
        branch = o.optStringSafe("head_branch"),
        event = o.optStringSafe("event"),
        runNumber = o.optInt("run_number"),
        createdAt = o.optStringSafe("created_at"),
    )

    private fun parseJob(o: JSONObject) = WorkflowJob(
        id = o.optLong("id"),
        name = o.optStringSafe("name").ifBlank { "Job" },
        status = o.optStringSafe("status"),
        conclusion = o.optStringSafe("conclusion").takeIf { it.isNotBlank() },
    )

    private companion object {
        const val API = "https://api.github.com"
        const val MAX_LOG_LINES = 4000
    }
}

/**
 * Android's `JSONObject.optString` returns the literal string "null" for an explicit JSON null
 * (`JSONObject.NULL`), which breaks `ifBlank`/`takeIf` fallbacks. This maps both a missing key and an
 * explicit null to "".
 */
private fun JSONObject.optStringSafe(key: String): String {
    val v = opt(key)
    return if (v == null || v == JSONObject.NULL) "" else v.toString()
}
