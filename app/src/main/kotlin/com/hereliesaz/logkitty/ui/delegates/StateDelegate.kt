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
import java.util.concurrent.atomic.AtomicLong

/**
 * A single log line with a strictly increasing identifier.
 *
 * The id is the basis for **per-tab clearing**: each tab records the highest id it has
 * "dismissed", and only lines with a larger id are rendered when that tab is selected.
 */
data class IndexedLogLine(val id: Long, val text: String)

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
 * 1. [com.hereliesaz.logkitty.utils.LogcatReader] pushes lines into the Channel as fast as they come.
 * 2. A dedicated coroutine reads from the Channel, buffers items for a short window ([BATCH_INTERVAL_MS]),
 *    and then emits them to the StateFlow in a single batch.
 *
 * This reduces UI updates from N/sec to roughly 10/sec (100ms interval), keeping the UI buttery smooth.
 */
class StateDelegate(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val MAX_LOG_SIZE = 5000
        private const val BATCH_INTERVAL_MS = 100L
    }

    private sealed interface LogEvent {
        data class System(val msg: String) : LogEvent
    }

    private val idCounter = AtomicLong(0L)
    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            val buffer = mutableListOf<LogEvent>()
            while (true) {
                val first = logChannel.receive()
                buffer.add(first)
                delay(BATCH_INTERVAL_MS)
                var result = logChannel.tryReceive()
                while (result.isSuccess) {
                    buffer.add(result.getOrThrow())
                    result = logChannel.tryReceive()
                }
                if (buffer.isNotEmpty()) {
                    processLogBatch(buffer)
                    buffer.clear()
                }
            }
        }
    }

    private fun processLogBatch(events: List<LogEvent>) {
        val systemLines = ArrayList<IndexedLogLine>()
        for (event in events) {
            when (event) {
                is LogEvent.System -> {
                    event.msg.split('\n').forEach { raw ->
                        if (raw.isNotBlank()) {
                            systemLines.add(IndexedLogLine(idCounter.incrementAndGet(), raw))
                        }
                    }
                }
            }
        }
        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
        }
    }

    private val _systemLog = MutableStateFlow<List<IndexedLogLine>>(emptyList())

    /**
     * Stream of indexed log lines. Each entry carries a unique increasing id so that
     * per-tab clearing can skip historical entries without dropping them for other tabs.
     */
    val systemLog = _systemLog.asStateFlow()

    /**
     * Highest id observed so far. Useful for "clear this tab" semantics where the caller
     * stores this value and later filters lines whose id is greater than the saved marker.
     */
    val currentMaxId: Long get() = idCounter.get()

    fun appendSystemLog(msg: String) {
        logChannel.trySend(LogEvent.System(msg))
    }

    private fun MutableStateFlow<List<IndexedLogLine>>.appendCapped(lines: List<IndexedLogLine>) {
        this.update { current ->
            val totalSize = current.size + lines.size
            if (totalSize <= MAX_LOG_SIZE) {
                current + lines
            } else {
                val keepFromCurrent = MAX_LOG_SIZE - lines.size
                if (keepFromCurrent <= 0) {
                    lines.takeLast(MAX_LOG_SIZE)
                } else {
                    val result = java.util.ArrayList<IndexedLogLine>(MAX_LOG_SIZE)
                    val start = current.size - keepFromCurrent
                    for (i in start until current.size) result.add(current[i])
                    result.addAll(lines)
                    result
                }
            }
        }
    }

    /**
     * Clears the global log buffer. Per-tab clearing is handled by the ViewModel via id markers.
     */
    fun clearLog() {
        _systemLog.value = emptyList()
    }
}
