package com.hereliesaz.logkitty.utils

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import com.hereliesaz.logkitty.model.AppStats
import com.hereliesaz.logkitty.model.CpuStats
import com.hereliesaz.logkitty.model.GpuStats
import com.hereliesaz.logkitty.model.HealthStats
import com.hereliesaz.logkitty.model.MemoryStats
import com.hereliesaz.logkitty.model.NetworkStats
import com.hereliesaz.logkitty.model.PowerStats
import com.hereliesaz.logkitty.model.ThreadCpu

/**
 * Gathers a full [AppStats] snapshot for one targeted package.
 *
 * **Design**
 * - A single root shell script (see [buildBatchScript]) collects everything proc/dumpsys-based in
 *   one `su` invocation per poll, to keep overhead low even while polling every couple of seconds.
 * - Network comes from [NetworkStatsManager] (public API, needs the "Usage access" permission) so it
 *   works even without root.
 * - Battery level/temperature come from the sticky `ACTION_BATTERY_CHANGED` intent (no permission).
 * - Rate metrics (CPU %, I/O B/s, network B/s) require two samples, so the collector remembers the
 *   previous reading. Call [reset] whenever the target package changes so stale deltas are dropped.
 *
 * The collector is **stateful and single-target**: hold one instance per active stats view.
 */
class AppStatsCollector(private val context: Context) {

    private var lastPackage: String? = null

    // --- Previous-sample state for delta/rate computation ---
    private var prevTotalJiffies = 0L
    private var prevIdleJiffies = 0L
    private var prevUtime = 0L
    private var prevStime = 0L
    private var prevThreadJiffies = HashMap<Int, Long>()
    private var prevIoRead = -1L
    private var prevIoWrite = -1L
    private var prevNetRx = -1L
    private var prevNetTx = -1L
    private var prevSampleNanos = 0L

    private val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /** Drops remembered deltas so the next snapshot starts a fresh rate baseline. */
    fun reset() {
        lastPackage = null
        prevTotalJiffies = 0L
        prevIdleJiffies = 0L
        prevUtime = 0L
        prevStime = 0L
        prevThreadJiffies = HashMap()
        prevIoRead = -1L
        prevIoWrite = -1L
        prevNetRx = -1L
        prevNetTx = -1L
        prevSampleNanos = 0L
    }

    /**
     * Collects the fast-changing sections (CPU, memory, GPU/frames, I/O, network). Power stats are
     * gathered separately via [collectPower] on a slower cadence because `dumpsys batterystats` is
     * heavy.
     */
    suspend fun collect(pkg: String, label: String, useRoot: Boolean): AppStats {
        if (pkg != lastPackage) {
            reset()
            lastPackage = pkg
        }
        val now = System.currentTimeMillis()
        val nowNanos = System.nanoTime()
        val dtSec = if (prevSampleNanos > 0) (nowNanos - prevSampleNanos) / 1e9 else 0.0
        val notes = mutableListOf<String>()

        // Network and battery work without root; gather them first so a non-rooted device still
        // shows something useful.
        val network = collectNetwork(pkg, dtSec, notes)

        if (!useRoot) {
            notes.add("Root is off — CPU, memory, GPU and I/O need root. Network is from Usage Access.")
            prevSampleNanos = nowNanos
            return AppStats(
                packageName = pkg, label = label, rootUsed = false, processFound = false,
                pids = emptyList(), collectedAtMs = now, network = network, notes = notes,
            )
        }

        val raw = RootShell.run(buildBatchScript(pkg), useRoot = true, timeoutMs = 5000)
        if (raw == null) {
            notes.add("Root shell unavailable — falling back to network-only stats.")
            prevSampleNanos = nowNanos
            return AppStats(
                packageName = pkg, label = label, rootUsed = false, processFound = false,
                pids = emptyList(), collectedAtMs = now, network = network, notes = notes,
            )
        }

        val sections = splitSections(raw)
        val pids = parsePids(sections["PIDS"])
        if (pids.isEmpty()) {
            notes.add("$label isn't running right now — start it to see CPU, memory and frame stats.")
            prevSampleNanos = nowNanos
            return AppStats(
                packageName = pkg, label = label, rootUsed = true, processFound = false,
                pids = emptyList(), collectedAtMs = now, network = network, notes = notes,
            )
        }

        val cpu = parseCpu(sections, pids)
        val memory = parseMemory(sections["MEMINFO"])
        val gpu = parseGpu(sections)
        val health = parseHealth(sections, pids, dtSec)

        prevSampleNanos = nowNanos
        return AppStats(
            packageName = pkg, label = label, rootUsed = true, processFound = true,
            pids = pids, collectedAtMs = now, cpu = cpu, memory = memory, gpu = gpu,
            network = network, health = health, notes = notes,
        )
    }

    /** Heavier, slower-changing power/scheduling stats. Safe to call less frequently. */
    suspend fun collectPower(pkg: String, useRoot: Boolean): PowerStats {
        val (level, temp, charging) = readBattery()
        if (!useRoot) {
            return PowerStats(null, null, null, null, level, temp, charging)
        }
        val raw = RootShell.run(buildPowerScript(pkg), useRoot = true, timeoutMs = 6000)
        val sections = if (raw != null) splitSections(raw) else emptyMap()
        val bs = sections["BATTERYSTATS"].orEmpty()
        val wakelockCount = Regex("Wake lock", RegexOption.IGNORE_CASE).findAll(bs).count().takeIf { it > 0 }
        val partialMs = parsePartialWakelockMs(bs)
        val jobs = sections["JOBS"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        val alarms = sections["ALARMS"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        return PowerStats(wakelockCount, partialMs, jobs, alarms, level, temp, charging)
    }

    // ---------------------------------------------------------------------------------------------
    // Shell scripts
    // ---------------------------------------------------------------------------------------------

    private fun buildBatchScript(pkg: String): String {
        val p = shellEscape(pkg)
        return """
            PIDS=${'$'}(pidof $p 2>/dev/null)
            if [ -z "${'$'}PIDS" ]; then PIDS=${'$'}(pgrep -f $p 2>/dev/null); fi
            echo "@@PIDS@@"; echo "${'$'}PIDS"
            echo "@@CPU@@"; head -n 1 /proc/stat 2>/dev/null
            echo "@@GPUPCT@@"; cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null
            echo "@@GPUBUSY@@"; cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null
            for pp in ${'$'}PIDS; do
              echo "@@PSTAT ${'$'}pp@@"; cat /proc/${'$'}pp/stat 2>/dev/null
              echo "@@PSTATUS ${'$'}pp@@"; cat /proc/${'$'}pp/status 2>/dev/null
              echo "@@PIO ${'$'}pp@@"; cat /proc/${'$'}pp/io 2>/dev/null
              echo "@@PFD ${'$'}pp@@"; ls /proc/${'$'}pp/fd 2>/dev/null | wc -l
              echo "@@PTASKS ${'$'}pp@@"; cat /proc/${'$'}pp/task/*/stat 2>/dev/null
            done
            echo "@@MEMINFO@@"; dumpsys meminfo $p 2>/dev/null
            echo "@@GFXINFO@@"; dumpsys gfxinfo $p 2>/dev/null
            echo "@@END@@"
        """.trimIndent()
    }

    private fun buildPowerScript(pkg: String): String {
        val p = shellEscape(pkg)
        return """
            echo "@@BATTERYSTATS@@"; dumpsys batterystats $p 2>/dev/null
            echo "@@JOBS@@"; dumpsys jobscheduler 2>/dev/null | grep -c $p
            echo "@@ALARMS@@"; dumpsys alarm 2>/dev/null | grep -c $p
            echo "@@END@@"
        """.trimIndent()
    }

    /** Wraps a package name in single quotes, neutralizing any embedded quote, for safe shell use. */
    private fun shellEscape(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ---------------------------------------------------------------------------------------------
    // Output parsing
    // ---------------------------------------------------------------------------------------------

    /** Splits the marker-delimited batch output into a map keyed by the marker (e.g. "PSTAT 1234"). */
    private fun splitSections(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var key: String? = null
        val buf = StringBuilder()
        for (line in raw.lineSequence()) {
            val m = MARKER.matchEntire(line.trim())
            if (m != null) {
                if (key != null) result[key!!] = buf.toString().trim()
                key = m.groupValues[1]
                buf.setLength(0)
            } else if (key != null) {
                buf.append(line).append('\n')
            }
        }
        if (key != null) result[key!!] = buf.toString().trim()
        return result
    }

    private fun parsePids(s: String?): List<Int> =
        s?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }?.distinct() ?: emptyList()

    private fun parseCpu(sections: Map<String, String>, pids: List<Int>): CpuStats? {
        val cpuLine = sections["CPU"]?.trim() ?: return null
        val nums = cpuLine.removePrefix("cpu").trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val total = nums.sum()
        val idle = nums[3] + (nums.getOrNull(4) ?: 0L) // idle + iowait

        var utime = 0L
        var stime = 0L
        for (pid in pids) {
            val stat = sections["PSTAT $pid"] ?: continue
            val (u, s) = parseStatCpu(stat) ?: continue
            utime += u
            stime += s
        }

        // Per-thread CPU (utime+stime), with library attribution from the thread name.
        val threadCur = HashMap<Int, Long>()
        val threadNames = HashMap<Int, String>()
        for (pid in pids) {
            val tasks = sections["PTASKS $pid"] ?: continue
            for (line in tasks.lineSequence()) {
                if (line.isBlank()) continue
                val tid = line.trim().substringBefore(' ').toIntOrNull() ?: continue
                val cpu = parseStatCpu(line) ?: continue
                threadCur[tid] = cpu.first + cpu.second
                threadNames[tid] = parseStatComm(line) ?: "tid $tid"
            }
        }

        val deltaTotal = total - prevTotalJiffies
        val haveBaseline = prevTotalJiffies > 0L && deltaTotal > 0L
        val appPercent: Float
        val userPercent: Float
        val kernelPercent: Float
        val deviceBusy: Float
        val threads: List<ThreadCpu>
        if (haveBaseline) {
            val dt = deltaTotal.toFloat()
            userPercent = 100f * (utime - prevUtime).coerceAtLeast(0) / dt
            kernelPercent = 100f * (stime - prevStime).coerceAtLeast(0) / dt
            appPercent = userPercent + kernelPercent
            deviceBusy = 100f * (deltaTotal - (idle - prevIdleJiffies)).coerceAtLeast(0) / dt
            threads = threadCur.mapNotNull { (tid, cur) ->
                val prev = prevThreadJiffies[tid] ?: return@mapNotNull null
                val pct = 100f * (cur - prev).coerceAtLeast(0) / dt
                if (pct <= 0.05f) null
                else ThreadCpu(tid, threadNames[tid] ?: "tid $tid", pct,
                    LibraryAttribution.forThread(threadNames[tid] ?: ""))
            }.sortedByDescending { it.percent }.take(12)
        } else {
            appPercent = 0f; userPercent = 0f; kernelPercent = 0f; deviceBusy = 0f; threads = emptyList()
        }

        prevTotalJiffies = total
        prevIdleJiffies = idle
        prevUtime = utime
        prevStime = stime
        prevThreadJiffies = threadCur

        return CpuStats(appPercent, userPercent, kernelPercent, numCores, deviceBusy, threads, threadCur.size)
    }

    /** Returns (utime, stime) jiffies from a `/proc/.../stat` line, handling spaces in the comm field. */
    private fun parseStatCpu(stat: String): Pair<Long, Long>? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        val after = stat.substring(close + 1).trim().split(Regex("\\s+"))
        // Fields after comm start at field 3 (state). utime = field 14 -> index 11; stime = 15 -> 12.
        val u = after.getOrNull(11)?.toLongOrNull() ?: return null
        val s = after.getOrNull(12)?.toLongOrNull() ?: return null
        return u to s
    }

    /** Extracts the `comm` (thread/process name) from a `/proc/.../stat` line. */
    private fun parseStatComm(stat: String): String? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        return stat.substring(open + 1, close)
    }

    private fun parseMemory(meminfo: String?): MemoryStats? {
        if (meminfo.isNullOrBlank()) return null
        fun field(label: String): Long? =
            Regex("""$label:\s+(\d+)""").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
        val total = field("TOTAL PSS") ?: Regex("""TOTAL\s+(\d+)""").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
        val (totalRam, availRam) = readDeviceRam()
        return MemoryStats(
            totalPssKb = total,
            javaHeapKb = field("Java Heap"),
            nativeHeapKb = field("Native Heap"),
            graphicsKb = field("Graphics"),
            codeKb = field("Code"),
            stackKb = field("Stack"),
            rssKb = field("TOTAL RSS"),
            deviceTotalRamKb = totalRam,
            deviceAvailRamKb = availRam,
        )
    }

    private fun parseGpu(sections: Map<String, String>): GpuStats? {
        val gfx = sections["GFXINFO"]
        var busyPct: Float? = sections["GPUPCT"]?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
        if (busyPct == null) {
            sections["GPUBUSY"]?.trim()?.split(Regex("\\s+"))?.let { parts ->
                val busy = parts.getOrNull(0)?.toFloatOrNull()
                val tot = parts.getOrNull(1)?.toFloatOrNull()
                if (busy != null && tot != null && tot > 0f) busyPct = 100f * busy / tot
            }
        }
        if (gfx.isNullOrBlank() && busyPct == null) return null

        fun longOf(re: String): Long? = gfx?.let { Regex(re).find(it)?.groupValues?.get(1)?.toLongOrNull() }
        fun floatOf(re: String): Float? = gfx?.let { Regex(re).find(it)?.groupValues?.get(1)?.toFloatOrNull() }
        return GpuStats(
            totalFrames = longOf("""Total frames rendered:\s*(\d+)"""),
            jankyFrames = longOf("""Janky frames:\s*(\d+)"""),
            jankPercent = floatOf("""Janky frames:\s*\d+\s*\(([\d.]+)%\)"""),
            p50Ms = floatOf("""50th percentile:\s*(\d+)ms"""),
            p90Ms = floatOf("""90th percentile:\s*(\d+)ms"""),
            p95Ms = floatOf("""95th percentile:\s*(\d+)ms"""),
            p99Ms = floatOf("""99th percentile:\s*(\d+)ms"""),
            missedVsync = longOf("""Number Missed Vsync:\s*(\d+)"""),
            slowUiThread = longOf("""Number Slow UI thread:\s*(\d+)"""),
            slowDrawCommands = longOf("""Number Slow issue draw commands:\s*(\d+)"""),
            gpuBusyPercent = busyPct,
        )
    }

    private fun parseHealth(sections: Map<String, String>, pids: List<Int>, dtSec: Double): HealthStats {
        var threads = 0
        var fds = 0
        var ioRead = 0L
        var ioWrite = 0L
        var sawIo = false
        for (pid in pids) {
            sections["PSTATUS $pid"]?.let { st ->
                Regex("""Threads:\s+(\d+)""").find(st)?.groupValues?.get(1)?.toIntOrNull()?.let { threads += it }
            }
            sections["PFD $pid"]?.trim()?.toIntOrNull()?.let { fds += it }
            sections["PIO $pid"]?.let { io ->
                Regex("""read_bytes:\s+(\d+)""").find(io)?.groupValues?.get(1)?.toLongOrNull()?.let { ioRead += it; sawIo = true }
                Regex("""write_bytes:\s+(\d+)""").find(io)?.groupValues?.get(1)?.toLongOrNull()?.let { ioWrite += it }
            }
        }
        var readRate: Long? = null
        var writeRate: Long? = null
        if (sawIo) {
            if (prevIoRead >= 0 && dtSec > 0) {
                readRate = ((ioRead - prevIoRead).coerceAtLeast(0) / dtSec).toLong()
                writeRate = ((ioWrite - prevIoWrite).coerceAtLeast(0) / dtSec).toLong()
            }
            prevIoRead = ioRead
            prevIoWrite = ioWrite
        }
        return HealthStats(
            threadCount = threads.takeIf { it > 0 },
            fdCount = fds.takeIf { it > 0 },
            ioReadBytesPerSec = readRate,
            ioWriteBytesPerSec = writeRate,
            totalIoReadBytes = if (sawIo) ioRead else null,
            totalIoWriteBytes = if (sawIo) ioWrite else null,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Public-API / no-root sources
    // ---------------------------------------------------------------------------------------------

    @Suppress("DEPRECATION") // ConnectivityManager.TYPE_* + queryDetailsForUid remain the per-UID path.
    private fun collectNetwork(pkg: String, dtSec: Double, notes: MutableList<String>): NetworkStats? {
        val uid = try {
            context.packageManager.getApplicationInfo(pkg, 0).uid
        } catch (e: Exception) { return null }

        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return null
        val end = System.currentTimeMillis()
        // A wide window so cumulative totals are stable; the per-poll delta gives the live rate.
        val start = end - 24L * 60 * 60 * 1000

        val wifi = queryUidBytes(nsm, ConnectivityManager.TYPE_WIFI, start, end, uid)
        val mobile = queryUidBytes(nsm, ConnectivityManager.TYPE_MOBILE, start, end, uid)
        if (wifi == null && mobile == null) {
            notes.add("Network stats need the Usage Access permission (Settings → Special access).")
            return null
        }
        val rx = (wifi?.first ?: 0L) + (mobile?.first ?: 0L)
        val tx = (wifi?.second ?: 0L) + (mobile?.second ?: 0L)

        var rxRate: Long? = null
        var txRate: Long? = null
        if (prevNetRx >= 0 && dtSec > 0) {
            rxRate = ((rx - prevNetRx).coerceAtLeast(0) / dtSec).toLong()
            txRate = ((tx - prevNetTx).coerceAtLeast(0) / dtSec).toLong()
        }
        prevNetRx = rx
        prevNetTx = tx

        return NetworkStats(
            rxBytesPerSec = rxRate,
            txBytesPerSec = txRate,
            totalRxBytes = rx,
            totalTxBytes = tx,
            mobileBytes = mobile?.let { it.first + it.second },
            wifiBytes = wifi?.let { it.first + it.second },
        )
    }

    /** Returns (rxBytes, txBytes) for [uid] over the window, or null if the query is denied/unsupported. */
    private fun queryUidBytes(
        nsm: NetworkStatsManager, networkType: Int, start: Long, end: Long, uid: Int,
    ): Pair<Long, Long>? {
        return try {
            @Suppress("DEPRECATION")
            val stats = nsm.queryDetailsForUid(networkType, null, start, end, uid)
            var rx = 0L
            var tx = 0L
            val bucket = android.app.usage.NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                rx += bucket.rxBytes
                tx += bucket.txBytes
            }
            stats.close()
            rx to tx
        } catch (e: Exception) {
            null
        }
    }

    private fun readBattery(): Triple<Int?, Float?, Boolean?> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return Triple(null, null, null)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temp = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            Triple(pct, temp, charging)
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    private fun readDeviceRam(): Pair<Long?, Long?> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem / 1024) to (mi.availMem / 1024)
        } catch (e: Exception) {
            null to null
        }
    }

    private fun parsePartialWakelockMs(bs: String): Long? {
        // batterystats prints partial wake locks as e.g. "Wake lock NAME: 1m 23s 456ms partial".
        var total = 0L
        var matched = false
        for (m in Regex("""(?:(\d+)h)?\s*(?:(\d+)m)?\s*(?:(\d+)s)?\s*(?:(\d+)ms)?\s+partial""").findAll(bs)) {
            val h = m.groupValues[1].toLongOrNull() ?: 0
            val min = m.groupValues[2].toLongOrNull() ?: 0
            val s = m.groupValues[3].toLongOrNull() ?: 0
            val ms = m.groupValues[4].toLongOrNull() ?: 0
            val sum = ((h * 60 + min) * 60 + s) * 1000 + ms
            if (sum > 0) { total += sum; matched = true }
        }
        return if (matched) total else null
    }

    companion object {
        private val MARKER = Regex("""@@([A-Z]+(?: \d+)?)@@""")
    }
}
