package com.hereliesaz.logkitty.ui.delegates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [StateDelegate] is the single source of truth for the raw log data.
 *
 * **Problem Solved:**
 * Android `logcat` can emit thousands of lines per second during high activity.
 * Sending every single line immediately to the UI (Jetpack Compose) would trigger thousands of
 * recompositions, causing the app to freeze (ANR) and consume massive battery.
 *
 * **Solution:**
 * This class implements a **Producer-Consumer** pattern using a Kotlin [Channel].
 * 1. [LogcatReader] pushes lines into the Channel as fast as they come (Producer).
 * 2. A dedicated coroutine reads from the Channel, buffers items for a short window ([BATCH_INTERVAL_MS]),
 *    and then emits them to the StateFlow in a single batch (Consumer).
 *
 * This reduces UI updates from N/sec to roughly 10/sec (100ms interval), keeping the UI buttery smooth.
 */
class StateDelegate(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        // Maximum number of lines to keep in memory. Oldest are dropped.
        private const val MAX_LOG_SIZE = 1000

        // Debounce interval. We collect logs for this duration before updating the UI.
        private const val BATCH_INTERVAL_MS = 100L
    }

    /**
     * Internal event wrapper. Currently only handles System logs, but extensible for other event types.
     */
    private sealed interface LogEvent {
        data class System(val msg: String) : LogEvent
    }

    // The unlimited channel ensures we don't block the LogcatReader if the processing lags slightly.
    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    init {
        // Start the processing loop on a background thread.
        scope.launch(dispatcher) {
            val buffer = mutableListOf<LogEvent>()
            while (true) {
                // 1. Wait for the first item (suspends until data is available).
                val first = logChannel.receive()
                buffer.add(first)

                // 2. Wait a fraction of a second to see if more logs arrive "at the same time".
                delay(BATCH_INTERVAL_MS)

                // 3. Drain the channel of all items that arrived during the delay.
                // tryReceive() is non-blocking.
                var result = logChannel.tryReceive()
                while (result.isSuccess) {
                    buffer.add(result.getOrThrow())
                    result = logChannel.tryReceive()
                }

                // 4. Process the accumulated batch in one go.
                if (buffer.isNotEmpty()) {
                    processLogBatch(buffer)
                    buffer.clear()
                }
            }
        }
    }

    /**
     * Processes a batch of events and updates the StateFlow.
     */
    private fun processLogBatch(events: List<LogEvent>) {
        val systemLines = ArrayList<String>()

        // Extract raw strings from the event wrappers.
        for (event in events) {
            when (event) {
                is LogEvent.System -> {
                    // Split multiline logs if necessary and filter empty lines.
                    event.msg.split('\n').filterTo(systemLines) { it.isNotBlank() }
                }
            }
        }

        // Only trigger a state update if we actually have valid lines.
        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
        }
    }

    // The backing MutableStateFlow. Initialize with empty list.
    private val _systemLog = MutableStateFlow<List<String>>(emptyList())

    /**
     * The public read-only stream of log lines.
     * The UI collects this to render the LazyColumn.
     */
    val systemLog = _systemLog.asStateFlow()

    /**
     * Entry point for the LogcatReader to add a line.
     * This is non-blocking (uses trySend on an UNLIMITED channel).
     */
    fun appendSystemLog(msg: String) {
        logChannel.trySend(LogEvent.System(msg))
    }

    /**
     * Extension function to append new lines while enforcing the [MAX_LOG_SIZE] cap.
     * Includes memory optimization to avoid allocating excessive temporary lists.
     */
    private fun MutableStateFlow<List<String>>.appendCapped(lines: List<String>) {
         this.update { current ->
            val totalSize = current.size + lines.size
            if (totalSize <= MAX_LOG_SIZE) {
                // If within limits, simple concatenation is fine.
                current + lines
            } else {
                // If we exceed the limit, we need to drop the oldest items.
                // Calculate how many items from the 'current' list we can keep.
                val keepFromCurrent = MAX_LOG_SIZE - lines.size

                if (keepFromCurrent <= 0) {
                    // Edge case: The incoming batch is larger than the entire buffer size.
                    // Just take the last N items of the incoming batch.
                    lines.takeLast(MAX_LOG_SIZE)
                } else {
                    // Optimization: Pre-allocate the ArrayList to the exact size needed.
                    val result = java.util.ArrayList<String>(MAX_LOG_SIZE)

                    // Copy the tail of the existing list.
                    // Accessing 'current' by index is O(1) assuming it's an ArrayList (standard in Kotlin).
                    val start = current.size - keepFromCurrent
                    for (i in start until current.size) {
                        result.add(current[i])
                    }

                    // Append all new lines.
                    result.addAll(lines)
                    result
                }
            }
        }
    }

    /**
     * Clears the current log buffer.
     */
    fun clearLog() {
        _systemLog.value = emptyList()
    }
}
