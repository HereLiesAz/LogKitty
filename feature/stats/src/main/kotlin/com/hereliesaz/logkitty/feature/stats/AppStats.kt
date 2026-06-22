package com.hereliesaz.logkitty.feature.stats

/**
 * A single immutable snapshot of everything LogKitty could measure about one targeted app at one
 * point in time. Every section is nullable: on a non-rooted device (or when a particular metric
 * isn't exposed by the kernel/ROM) the section is simply `null` and the UI renders a graceful
 * "unavailable" placeholder instead of a fake zero.
 *
 * Snapshots are produced by [com.hereliesaz.logkitty.utils.AppStatsCollector] and streamed to the
 * UI by [com.hereliesaz.logkitty.ui.delegates.StatsDelegate].
 */
data class AppStats(
    val packageName: String,
    val label: String,
    /** True when this snapshot was gathered through a root shell (full fidelity). */
    val rootUsed: Boolean,
    /** True when the process was found and at least the proc-based sections were populated. */
    val processFound: Boolean,
    /** Process ids backing the app (multi-process apps report several). */
    val pids: List<Int>,
    val collectedAtMs: Long,
    val cpu: CpuStats? = null,
    val memory: MemoryStats? = null,
    val gpu: GpuStats? = null,
    val network: NetworkStats? = null,
    val power: PowerStats? = null,
    val health: HealthStats? = null,
    val components: ComponentStats? = null,
    val sensors: SensorStats? = null,
    val crashes: CrashStats? = null,
    val binder: BinderStats? = null,
    /** Human-readable caveats, e.g. "Root required for CPU, memory, GPU and I/O stats". */
    val notes: List<String> = emptyList(),
)

/**
 * CPU usage expressed as a percentage of the whole device's capacity (0..100, summed across all
 * cores). The per-thread breakdown is the closest thing to "which library is taxing the device"
 * that can be measured from outside the process — most libraries name their threads.
 */
data class CpuStats(
    val appPercent: Float,
    val userPercent: Float,
    val kernelPercent: Float,
    val numCores: Int,
    val deviceBusyPercent: Float,
    /** Top threads by CPU, descending. Each carries the library it most likely belongs to. */
    val threads: List<ThreadCpu>,
    val threadCount: Int,
)

data class ThreadCpu(
    val tid: Int,
    val name: String,
    val percent: Float,
    /** Best-effort library/subsystem the thread belongs to (e.g. "OkHttp", "ART GC"); null if unknown. */
    val library: String?,
    /** Owning process id, so an on-demand stack probe can read /proc/<pid>/task/<tid>/stack. */
    val pid: Int = 0,
)

data class MemoryStats(
    val totalPssKb: Long?,
    val javaHeapKb: Long?,
    val nativeHeapKb: Long?,
    val graphicsKb: Long?,
    val codeKb: Long?,
    val stackKb: Long?,
    val rssKb: Long?,
    val deviceTotalRamKb: Long?,
    val deviceAvailRamKb: Long?,
)

data class GpuStats(
    val totalFrames: Long?,
    val jankyFrames: Long?,
    val jankPercent: Float?,
    val p50Ms: Float?,
    val p90Ms: Float?,
    val p95Ms: Float?,
    val p99Ms: Float?,
    val missedVsync: Long?,
    val slowUiThread: Long?,
    val slowDrawCommands: Long?,
    /** System-wide GPU busy %, device-specific (Adreno/KGSL). Not per-app. */
    val gpuBusyPercent: Float?,
)

data class NetworkStats(
    val rxBytesPerSec: Long?,
    val txBytesPerSec: Long?,
    val totalRxBytes: Long?,
    val totalTxBytes: Long?,
    val mobileBytes: Long?,
    val wifiBytes: Long?,
)

data class PowerStats(
    val wakelockCount: Int?,
    val partialWakelockMs: Long?,
    val jobsRegistered: Int?,
    val alarmsRegistered: Int?,
    val batteryLevelPercent: Int?,
    val batteryTempC: Float?,
    val charging: Boolean?,
    // --- Battery drill-down detail ("what's draining battery"), best-effort, root only ---
    /** Top partial wakelocks by held time. */
    val topWakelocks: List<NamedDuration> = emptyList(),
    /** Scheduled-job components attributed to the app. */
    val jobComponents: List<String> = emptyList(),
    /** Alarm receivers/actions attributed to the app. */
    val alarmComponents: List<String> = emptyList(),
)

/** A named thing with an optional held/elapsed duration in milliseconds (e.g. a wakelock). */
data class NamedDuration(val name: String, val ms: Long?)

data class HealthStats(
    val threadCount: Int?,
    val fdCount: Int?,
    val ioReadBytesPerSec: Long?,
    val ioWriteBytesPerSec: Long?,
    val totalIoReadBytes: Long?,
    val totalIoWriteBytes: Long?,
)

/** Running components for the app, parsed from `dumpsys activity services` (root, best-effort). */
data class ComponentStats(
    val runningServiceCount: Int?,
    /** Short service class names currently running (deduped, capped). */
    val runningServices: List<String>,
)

/**
 * Sensor and location usage for the app, parsed from `dumpsys sensorservice` / `dumpsys location`
 * (root, best-effort). Tells you whether the app is quietly holding a sensor or GPS request — a
 * common, hard-to-spot battery drain.
 */
data class SensorStats(
    /** Number of active sensor connections the app currently holds. */
    val activeSensorConnections: Int?,
    /** Distinct sensor types the app is subscribed to (keyword-matched from the dump). */
    val activeSensors: List<String>,
    /** True if the app currently holds a location request; null when it can't be determined. */
    val locationActive: Boolean?,
    /** Location providers the app is requesting (e.g. "gps", "fused", "network"). */
    val locationProviders: List<String>,
)

/**
 * Recent crash / ANR history for the app, read from the logcat **crash** buffer and the system log.
 * Unlike most sections this works **without root** — it only needs the `READ_LOGS` grant the app
 * already relies on. Timestamps are the raw logcat "MM-DD HH:MM:SS" string (no year/zone guessing).
 */
data class CrashStats(
    val crashCount: Int,
    val anrCount: Int,
    val lastCrashWhen: String?,
    val lastCrashSummary: String?,
    val lastAnrWhen: String?,
    val lastAnrSummary: String?,
)

/**
 * Binder / IPC footprint, parsed from `/sys/kernel/debug/binder/proc/<pid>` (root + debugfs,
 * best-effort). High node/ref counts or pending transactions hint at chatty or leaking IPC.
 */
data class BinderStats(
    val threads: Int?,
    val nodes: Int?,
    val refs: Int?,
    val pendingTransactions: Int?,
)
