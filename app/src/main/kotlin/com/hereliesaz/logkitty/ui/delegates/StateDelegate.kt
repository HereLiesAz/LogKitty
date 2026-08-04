package com.hereliesaz.logkitty.ui.delegates

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * A single log line with a strictly increasing identifier.
 *
 * The id is the basis for **per-tab clearing**: each tab records the highest id it has
 * "dismissed", and only lines with a larger id are rendered when that tab is selected.
 *
 * [uid] is the Android UID of the process that emitted the line, parsed from the `-v uid`
 * column (see [StateDelegate.parseUidPrefix]). It powers reliable per-app filtering. It is
 * `null` for lines without a recognizable UID prefix (e.g. reader warnings or stack-trace
 * continuation lines).
 */
data class IndexedLogLine(val id: Long, val text: String, val uid: Int? = null)

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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    bufferSizeFlow: kotlinx.coroutines.flow.StateFlow<Int>? = null,
) {
    companion object {
        private const val DEFAULT_maxLogSize = 5000
        private const val BATCH_INTERVAL_MS = 100L

        /** Anchors the start of a standard `-v time` line: `MM-DD HH:MM:SS.mmm`. */
        private val TIMESTAMP_ANCHOR = Regex("""\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}""")

        /** App-UID name form printed by some devices' `-v uid`, e.g. `u0_a123`. */
        private val APP_UID_NAME = Regex("""^u(\d+)_a(\d+)$""")

        /** Result of parsing a raw logcat line into its UID prefix and display text. */
        internal data class ParsedLogLine(val uid: Int?, val text: String, val hasTimestamp: Boolean)

        /**
         * Splits the optional `-v uid` prefix off a raw logcat line.
         *
         * The line shape is `<uid> MM-DD HH:MM:SS.mmm L/Tag( pid): msg`. We locate the timestamp
         * and treat everything before it as the UID token, returning the remainder as the
         * display [text] so it matches the plain `-v time` format every downstream parser expects.
         * Lines with no timestamp (reader warnings, wrapped stack-trace lines) are reported with
         * [hasTimestamp]`= false` so the caller can attribute continuation lines to the preceding
         * entry's UID.
         */
        internal fun parseUidPrefix(raw: String): ParsedLogLine {
            val match = TIMESTAMP_ANCHOR.find(raw) ?: return ParsedLogLine(null, raw, false)
            val start = match.range.first
            if (start == 0) return ParsedLogLine(null, raw, true)
            val prefix = raw.substring(0, start).trim()
            val uid = parseUid(prefix)
            return if (uid != null) {
                ParsedLogLine(uid, raw.substring(start), true)
            } else {
                ParsedLogLine(null, raw, false)
            }
        }

        /**
         * Converts a logcat UID token to a numeric Android UID. Handles plain numbers and the
         * `u<user>_a<appId>` app-UID name form; returns `null` for anything else.
         */
        internal fun parseUid(token: String): Int? {
            token.toIntOrNull()?.let { return it }
            APP_UID_NAME.matchEntire(token)?.let { m ->
                val user = m.groupValues[1].toIntOrNull() ?: return null
                val appId = m.groupValues[2].toIntOrNull() ?: return null
                // App UIDs for user N: N * 100000 + (10000 + appId).
                return user * 100_000 + 10_000 + appId
            }
            return null
        }
    }

    @Volatile
    private var maxLogSize: Int = bufferSizeFlow?.value ?: DEFAULT_maxLogSize

    private sealed interface LogEvent {
        data class System(val msg: String) : LogEvent
    }

    private val idCounter = AtomicLong(0L)
    private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

    // UID of the most recent log entry that carried a timestamp. Header-less continuation lines
    // (e.g. stack traces) inherit it so they stay attributed to the same app. Only ever touched
    // from the single batch-processing coroutine below, so no synchronization is needed.
    private var lastSeenUid: Int? = null

    private val targetUids = MutableStateFlow<Set<Int>>(emptySet())
    private val targetPkgs = MutableStateFlow<Set<String>>(emptySet())

    fun setTargetApps(uids: Set<Int>, pkgs: Set<String>) {
        targetUids.value = uids
        targetPkgs.value = pkgs
    }

    init {
        if (bufferSizeFlow != null) {
            scope.launch(dispatcher) {
                bufferSizeFlow.collect { maxLogSize = it }
            }
        }

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
                            val parsed = parseUidPrefix(raw)
                            val uid = if (parsed.hasTimestamp) {
                                // New log entry: trust its own UID (may be null) and remember it.
                                lastSeenUid = parsed.uid
                                parsed.uid
                            } else {
                                // Header-less continuation line (stack trace, wrapped message):
                                // inherit the preceding entry's UID so it isn't dropped from the app tab.
                                parsed.uid ?: lastSeenUid
                            }
                            if (isJunk(parsed.text)) return@forEach

                            val uids = targetUids.value
                            val pkgs = targetPkgs.value
                            
                            val isTarget = if (uids.isNotEmpty() || pkgs.isNotEmpty()) {
                                if (uid != null && uids.contains(uid)) true
                                else pkgs.any { pkg -> parsed.text.contains(pkg, ignoreCase = true) }
                            } else true // If no targets specified, keep all (or should we drop? Let's keep all for safety if no targets)

                            if (isTarget) {
                                systemLines.add(IndexedLogLine(idCounter.incrementAndGet(), parsed.text, uid))
                            }
                        }
                    }
                }
            }
        }
        if (systemLines.isNotEmpty()) {
            _systemLog.appendCapped(systemLines)
            _newLinesChannel.trySend(systemLines)
        }
    }

    private fun isJunk(text: String): Boolean {
        // Basic junk filtering for common spammy tags
        if (text.contains("SurfaceFlinger:") && text.contains("composite")) return true
        if (text.contains("BatteryStatsService:") && text.contains("update")) return true
        if (text.contains("NetworkStats:") && text.contains("performPoll")) return true
        if (text.contains("Choreographer:") && text.contains("skipped")) return true
        return false
    }

    private val _newLinesChannel = kotlinx.coroutines.channels.Channel<List<IndexedLogLine>>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val newLinesEvent = _newLinesChannel.receiveAsFlow()

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
            if (totalSize <= maxLogSize) {
                current + lines
            } else {
                val keepFromCurrent = maxLogSize - lines.size
                if (keepFromCurrent <= 0) {
                    lines.takeLast(maxLogSize)
                } else {
                    val result = java.util.ArrayList<IndexedLogLine>(maxLogSize)
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
