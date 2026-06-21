package com.hereliesaz.logkitty.feature.github

/**
 * Plain data models for the GitHub Actions panel, populated by [GitHubApi] from REST JSON via
 * `org.json` (so the module needs no serialization plugin/dependency).
 */

/** One workflow run (`/actions/runs`). */
data class WorkflowRun(
    val id: Long,
    val name: String,
    /** "queued" | "in_progress" | "completed" (and occasionally others). */
    val status: String,
    /** "success" | "failure" | "cancelled" | "skipped" | … ; null while not completed. */
    val conclusion: String?,
    val branch: String,
    val event: String,
    val runNumber: Int,
    val createdAt: String,
) {
    /** True once the run has reached a terminal state (no more polling needed). */
    val isTerminal: Boolean get() = status == "completed"
}

/** One job within a run (`/actions/runs/{id}/jobs`). */
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
) {
    val isTerminal: Boolean get() = status == "completed"
}

/** Result wrapper so the UI can distinguish loading failures (auth, network, not-found) cleanly. */
sealed interface GitHubResult<out T> {
    data class Ok<T>(val value: T) : GitHubResult<T>
    /** [message] is user-facing; [unauthorized] flags a bad/missing token so the UI can prompt re-auth. */
    data class Err(val message: String, val unauthorized: Boolean = false) : GitHubResult<Nothing>
}
