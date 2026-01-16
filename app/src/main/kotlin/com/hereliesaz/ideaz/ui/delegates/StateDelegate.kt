package com.hereliesaz.ideaz.ui.delegates

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
 * Delegate responsible for holding and managing shared UI state.
 * Centralizes state flows for logs.
 *
 * Performance Note:
 * Implements log batching via a Channel to prevent O(N^2) list copying and excessive UI recompositions
 * when logs are streaming in rapidly.
 */
class StateDelegate(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val MAX_LOG_SIZE = 1000
        private const val BATCH_INTERVAL_MS = 100L
    }

    private sealed interface LogEvent {
        data class System(val msg: String) : LogEvent
    }

    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            val buffer = mutableListOf<LogEvent>()
            while (true) {
                // Wait for the first item
                val first = logChannel.receive()
                buffer.add(first)

                // Wait a bit to collect more items (debouncing/batching)
                delay(BATCH_INTERVAL_MS)

                // Drain the channel of currently available items
                var result = logChannel.tryReceive()
                while (result.isSuccess) {
                    buffer.add(result.getOrThrow())
                    result = logChannel.tryReceive()
                }

                // Process the batch
                if (buffer.isNotEmpty()) {
                    processLogBatch(buffer)
                    buffer.clear()
                }
            }
        }
    }

    private fun processLogBatch(events: List<LogEvent>) {
        val systemLines = ArrayList<String>()

        // Single pass to categorize logs
        for (event in events) {
            when (event) {
                is LogEvent.System -> {
                    event.msg.split('\n').filterTo(systemLines) { it.isNotBlank() }
                }
            }
        }

        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
        }
    }

    private val _systemLog = MutableStateFlow<List<String>>(emptyList())
    /** The system logcat stream. Capped at [MAX_LOG_SIZE] lines. */
    val systemLog = _systemLog.asStateFlow()

    private val _bottomSheetState = MutableStateFlow<com.composables.core.SheetDetent>(com.composables.core.SheetDetent.Hidden)
    val bottomSheetState = _bottomSheetState.asStateFlow()
    fun setBottomSheetState(s: com.composables.core.SheetDetent) { _bottomSheetState.value = s }

    fun appendSystemLog(msg: String) {
        logChannel.trySend(LogEvent.System(msg))
    }

    /** Helper to append lines with a cap, reusing list creation logic. */
    private fun MutableStateFlow<List<String>>.appendCapped(lines: List<String>) {
         this.update { current ->
            val totalSize = current.size + lines.size
            if (totalSize <= MAX_LOG_SIZE) {
                current + lines
            } else {
                // Optimization: Avoid creating an intermediate list of size (current + lines)
                // just to slice it. Instead, build the result directly.
                val keepFromCurrent = MAX_LOG_SIZE - lines.size
                if (keepFromCurrent <= 0) {
                    lines.takeLast(MAX_LOG_SIZE)
                } else {
                    val result = java.util.ArrayList<String>(MAX_LOG_SIZE)
                    // We assume 'current' is RandomAccess (ArrayList) for O(1) access
                    val start = current.size - keepFromCurrent
                    for (i in start until current.size) {
                        result.add(current[i])
                    }
                    result.addAll(lines)
                    result
                }
            }
        }
    }

    /** Clears all logs. */
    fun clearLog() {
        _systemLog.value = emptyList()
    }
}
