package com.hereliesaz.logkitty.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.utils.GitHubCredentials
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Watches one GitHub Actions run to completion and posts a ✅/❌ notification when it finishes.
 *
 * Runs in the base process (so it can read the PAT from [GitHubCredentials] directly) and survives
 * the panel being closed and even process death — which `produceState` polling cannot. It's a plain
 * [Worker] (synchronous `doWork` on WorkManager's background executor), so it can block on OkHttp and
 * needs no `work-runtime-ktx`/coroutines. While the run is still going it returns [Result.retry] and
 * WorkManager re-runs it with linear backoff, up to [MAX_ATTEMPTS].
 */
class RunWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER) ?: return Result.failure()
        val repo = inputData.getString(KEY_REPO) ?: return Result.failure()
        val runId = inputData.getLong(KEY_RUN_ID, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val runName = inputData.getString(KEY_RUN_NAME) ?: "Workflow run"

        val token = GitHubCredentials(applicationContext).readToken()
        val status = when (val r = fetchRunStatus(owner, repo, runId, token)) {
            is FetchResult.Success -> r.status
            // Auth/not-found/bad-request won't fix themselves — fail instead of retrying 80×.
            FetchResult.Permanent -> return Result.failure()
            FetchResult.Transient -> return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        if (status.terminal) {
            postCompletion(owner, repo, runId, runName, status.conclusion)
            return Result.success()
        }
        // Still running — try again later (give up quietly after MAX_ATTEMPTS so we don't poll forever).
        return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private data class RunStatus(val terminal: Boolean, val conclusion: String?)

    private sealed interface FetchResult {
        data class Success(val status: RunStatus) : FetchResult
        /** Retryable (network blip, 5xx, rate-limit). */
        data object Transient : FetchResult
        /** Won't change on retry (401/403/404/400) — stop. */
        data object Permanent : FetchResult
    }

    private fun fetchRunStatus(owner: String, repo: String, runId: Long, token: String?): FetchResult {
        val builder = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        token?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val o = JSONObject(resp.body?.string().orEmpty())
                        val statusStr = o.optString("status")
                        val conclusion = o.opt("conclusion")?.takeIf { it != JSONObject.NULL }?.toString()
                        FetchResult.Success(RunStatus(terminal = statusStr == "completed", conclusion = conclusion))
                    }
                    resp.code == 400 || resp.code == 401 || resp.code == 403 || resp.code == 404 -> FetchResult.Permanent
                    else -> FetchResult.Transient
                }
            }
        } catch (e: Exception) {
            FetchResult.Transient
        }
    }

    private fun postCompletion(owner: String, repo: String, runId: Long, runName: String, conclusion: String?) {
        val success = conclusion == "success"
        val glyph = when (conclusion) {
            "success" -> "✅"
            "failure", "timed_out" -> "❌"
            "cancelled" -> "🚫"
            else -> "⚠️"
        }
        val title = applicationContext.getString(
            if (success) R.string.github_notif_passed else R.string.github_notif_finished, glyph,
        )
        val text = applicationContext.getString(R.string.github_notif_run, "$owner/$repo", runName)

        ensureChannel()
        val tapIntent = Intent(applicationContext, MainActivity::class.java)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        // Run IDs are Long and overflow Int, so don't truncate: hash for the request code, and tag the
        // notification by run id (with a fixed numeric id) so distinct runs don't collide/overwrite.
        val pending = PendingIntent.getActivity(
            applicationContext, runId.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        // notify() throws nothing if POST_NOTIFICATIONS is missing; it's a silent no-op on 33+.
        try {
            NotificationManagerCompat.from(applicationContext).notify("watch_run_$runId", 0, notification)
        } catch (e: SecurityException) {
            // Notifications not permitted — nothing more to do.
        }
    }

    private fun ensureChannel() {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.github_notif_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "logkitty_github_channel"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
        private const val KEY_RUN_ID = "run_id"
        private const val KEY_RUN_NAME = "run_name"
        private const val MAX_ATTEMPTS = 80

        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        /** Enqueues a watch for [runId]; KEEP so re-watching the same run doesn't stack duplicates. */
        fun enqueue(context: Context, owner: String, repo: String, runId: Long, runName: String) {
            val data = Data.Builder()
                .putString(KEY_OWNER, owner)
                .putString(KEY_REPO, repo)
                .putLong(KEY_RUN_ID, runId)
                .putString(KEY_RUN_NAME, runName)
                .build()
            val request = OneTimeWorkRequest.Builder(RunWatchWorker::class.java)
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("watch_run_$runId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
