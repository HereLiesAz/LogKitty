package com.hereliesaz.logkitty.feature.github

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The GitHub Actions drill-down: runs → jobs → job log. Phase 2 does a single fetch per screen with
 * a manual refresh; live polling (Phase 4) and ANSI coloring (Phase 3) build on this.
 */
@Composable
fun GitHubActionsPanel(
    owner: String,
    repo: String,
    tokenProvider: () -> String?,
    fontFamily: FontFamily?,
    fontSize: Int,
    onWatchRun: (owner: String, repo: String, runId: Long, runName: String) -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier,
) {
    if (owner.isBlank() || repo.isBlank()) {
        Centered(modifier) {
            Text(
                "Set a GitHub owner and repository in Settings to see workflow runs.",
                color = Color.White.copy(alpha = 0.85f), fontSize = fontSize.sp, fontFamily = fontFamily,
            )
            Button(onClick = onConfigure) { Text("Open Settings") }
        }
        return
    }

    // No key: the lambda always reads the current token via the ViewModel, so a stable instance is
    // fine and avoids rebuilding the OkHttp client on every recomposition.
    val api = remember { GitHubApi(tokenProvider) }
    var selectedRun by remember(owner, repo) { mutableStateOf<WorkflowRun?>(null) }
    var selectedJob by remember(owner, repo) { mutableStateOf<WorkflowJob?>(null) }

    when {
        selectedJob != null -> JobLogScreen(
            api, owner, repo, selectedJob!!, fontFamily, fontSize, onBack = { selectedJob = null },
        )
        selectedRun != null -> JobsScreen(
            api, owner, repo, selectedRun!!, fontFamily, fontSize,
            onBack = { selectedRun = null },
            onJob = { selectedJob = it },
            onWatch = { onWatchRun(owner, repo, selectedRun!!.id, selectedRun!!.name) },
            modifier = modifier,
        )
        else -> RunsScreen(api, owner, repo, fontFamily, fontSize, onRun = { selectedRun = it }, modifier = modifier)
    }
}

@Composable
private fun RunsScreen(
    api: GitHubApi, owner: String, repo: String, ff: FontFamily?, fs: Int,
    onRun: (WorkflowRun) -> Unit, modifier: Modifier,
) {
    var refresh by remember { mutableStateOf(0) }
    val state by produceState<GitHubResult<List<WorkflowRun>>?>(null, owner, repo, refresh) {
        value = null
        value = api.listRuns(owner, repo)
    }
    Column(modifier = modifier.fillMaxSize()) {
        Header("$owner/$repo", ff, fs, onBack = null, onRefresh = { refresh++ })
        ResultBody(state, ff, fs, empty = "No workflow runs.") { runs ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(runs, key = { it.id }) { run ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onRun(run) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusGlyph(run.status, run.conclusion), fontSize = (fs + 2).sp, modifier = Modifier.padding(end = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(run.name, color = Color.White, fontSize = fs.sp, fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "#${run.runNumber} · ${run.branch} · ${run.event}",
                                color = Color.White.copy(alpha = 0.6f), fontSize = (fs - 3).sp, fontFamily = ff,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobsScreen(
    api: GitHubApi, owner: String, repo: String, run: WorkflowRun, ff: FontFamily?, fs: Int,
    onBack: () -> Unit, onJob: (WorkflowJob) -> Unit, onWatch: () -> Unit, modifier: Modifier,
) {
    var refresh by remember { mutableStateOf(0) }
    val state by produceState<GitHubResult<List<WorkflowJob>>?>(null, run.id, refresh) {
        value = null
        value = api.listJobs(owner, repo, run.id)
    }
    Column(modifier = modifier.fillMaxSize()) {
        Header("${statusGlyph(run.status, run.conclusion)} ${run.name}", ff, fs, onBack = onBack, onRefresh = { refresh++ })
        if (!run.isTerminal) {
            TextButton(onClick = onWatch, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Notify me when this run finishes", fontSize = (fs - 2).sp)
            }
        }
        ResultBody(state, ff, fs, empty = "No jobs.") { jobs ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(jobs, key = { it.id }) { job ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onJob(job) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusGlyph(job.status, job.conclusion), fontSize = (fs + 2).sp, modifier = Modifier.padding(end = 8.dp))
                        Text(job.name, color = Color.White, fontSize = fs.sp, fontFamily = ff, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun JobLogScreen(
    api: GitHubApi, owner: String, repo: String, job: WorkflowJob, ff: FontFamily?, fs: Int, onBack: () -> Unit,
) {
    var refresh by remember { mutableStateOf(0) }
    val state by produceState<GitHubResult<List<String>>?>(null, job.id, refresh) {
        value = null
        value = api.fetchJobLog(owner, repo, job.id)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Header("${statusGlyph(job.status, job.conclusion)} ${job.name}", ff, fs, onBack = onBack, onRefresh = { refresh++ })
        ResultBody(state, ff, fs, empty = "No log output.") { lines ->
            // Colored, collapsible, selectable rendering (ANSI + ##[group]/##[error]/…).
            GitHubLogView(logLines = lines, fontFamily = ff, fontSize = fs, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun <T> ResultBody(
    state: GitHubResult<T>?, ff: FontFamily?, fs: Int, empty: String,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        null -> Centered(Modifier.fillMaxSize()) { CircularProgressIndicator() }
        is GitHubResult.Err -> Centered(Modifier.fillMaxSize()) {
            Text(state.message, color = Color(0xFFE57373), fontSize = fs.sp, fontFamily = ff)
        }
        is GitHubResult.Ok -> {
            val v = state.value
            if (v is List<*> && v.isEmpty()) {
                Centered(Modifier.fillMaxSize()) { Text(empty, color = Color.Gray, fontSize = fs.sp, fontFamily = ff) }
            } else {
                content(v)
            }
        }
    }
}

@Composable
private fun Header(title: String, ff: FontFamily?, fs: Int, onBack: (() -> Unit)?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ Back", fontSize = (fs - 1).sp) }
        }
        Text(
            title, color = Color.White, fontSize = fs.sp, fontFamily = ff, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        TextButton(onClick = onRefresh) { Text("Refresh", fontSize = (fs - 1).sp) }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

/** Status emoji for a run/job's status + conclusion. */
private fun statusGlyph(status: String, conclusion: String?): String = when {
    status != "completed" && status.isNotBlank() -> if (status == "queued") "⚪" else "🟡"
    conclusion == "success" -> "✅"
    conclusion == "failure" || conclusion == "timed_out" -> "❌"
    conclusion == "cancelled" -> "🚫"
    conclusion == "skipped" -> "⏭️"
    else -> "⚠️"
}
