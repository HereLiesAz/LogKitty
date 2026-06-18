package com.hereliesaz.logkitty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.logkitty.model.AppStats
import com.hereliesaz.logkitty.model.CpuStats
import com.hereliesaz.logkitty.model.GpuStats
import com.hereliesaz.logkitty.model.HealthStats
import com.hereliesaz.logkitty.model.MemoryStats
import com.hereliesaz.logkitty.model.NetworkStats
import com.hereliesaz.logkitty.model.PowerStats

/**
 * The developer-stats body shown when an app tab is toggled from Logs to Stats. Renders the live
 * [AppStats] snapshot as a set of scrollable sections. Any unavailable section/metric shows a "—"
 * placeholder rather than a misleading zero, and root/permission caveats appear as banners up top.
 */
@Composable
fun StatsView(
    stats: AppStats?,
    fontFamily: FontFamily?,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    if (stats == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Collecting stats…", color = Color.Gray, fontSize = fontSize.sp, fontFamily = fontFamily)
        }
        return
    }

    val labelColor = Color.White.copy(alpha = 0.65f)
    val valueColor = Color.White

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header: which app, and how the data was gathered.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stats.label,
                color = valueColor,
                fontSize = (fontSize + 2).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val mode = when {
                stats.rootUsed && stats.processFound -> "root • live"
                stats.rootUsed -> "root"
                else -> "best-effort"
            }
            Text(mode, color = labelColor, fontSize = (fontSize - 2).sp, fontFamily = fontFamily)
        }
        if (stats.pids.isNotEmpty()) {
            Text(
                "${stats.packageName}  ·  pid ${stats.pids.joinToString(", ")}",
                color = labelColor, fontSize = (fontSize - 3).sp, fontFamily = fontFamily,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        stats.notes.forEach { note -> NoteBanner(note, fontFamily, fontSize) }

        stats.cpu?.let { CpuSection(it, fontFamily, fontSize, labelColor, valueColor) }
        stats.memory?.let { MemorySection(it, fontFamily, fontSize, labelColor, valueColor) }
        stats.gpu?.let { GpuSection(it, fontFamily, fontSize, labelColor, valueColor) }
        stats.network?.let { NetworkSection(it, fontFamily, fontSize, labelColor, valueColor) }
        stats.power?.let { PowerSection(it, fontFamily, fontSize, labelColor, valueColor) }
        stats.health?.let { HealthSection(it, fontFamily, fontSize, labelColor, valueColor) }

        Spacer(Modifier.height(16.dp))
    }
}

// ----------------------------------------------------------------------------------------------
// Sections
// ----------------------------------------------------------------------------------------------

@Composable
private fun CpuSection(cpu: CpuStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("CPU", ff, fs) {
        PercentBar("App CPU (of device)", cpu.appPercent, 100f, ff, fs, lc, vc)
        StatRow("User / Kernel", "${pct(cpu.userPercent)} / ${pct(cpu.kernelPercent)}", ff, fs, lc, vc)
        StatRow("Device busy", pct(cpu.deviceBusyPercent), ff, fs, lc, vc)
        StatRow("Cores", cpu.numCores.toString(), ff, fs, lc, vc)
        if (cpu.threads.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Top threads — the library behind the load",
                color = lc, fontSize = (fs - 2).sp, fontFamily = ff, fontWeight = FontWeight.Medium,
            )
            cpu.threads.forEach { t ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.name, color = vc, fontSize = (fs - 1).sp, fontFamily = ff,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        t.library?.let {
                            Text(it, color = accentFor(it), fontSize = (fs - 3).sp, fontFamily = ff)
                        }
                    }
                    Text(pct(t.percent), color = vc, fontSize = (fs - 1).sp, fontFamily = ff,
                        fontWeight = FontWeight.Bold)
                }
            }
        } else {
            StatRow("Top threads", "sampling… (needs 2 reads)", ff, fs, lc, vc)
        }
    }
}

@Composable
private fun MemorySection(m: MemoryStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("Memory", ff, fs) {
        StatRow("Total PSS", kb(m.totalPssKb), ff, fs, lc, vc, emphasize = true)
        StatRow("Java heap", kb(m.javaHeapKb), ff, fs, lc, vc)
        StatRow("Native heap", kb(m.nativeHeapKb), ff, fs, lc, vc)
        StatRow("Graphics", kb(m.graphicsKb), ff, fs, lc, vc)
        StatRow("Code", kb(m.codeKb), ff, fs, lc, vc)
        StatRow("Stack", kb(m.stackKb), ff, fs, lc, vc)
        StatRow("Total RSS", kb(m.rssKb), ff, fs, lc, vc)
        if (m.deviceTotalRamKb != null) {
            val used = m.deviceTotalRamKb - (m.deviceAvailRamKb ?: 0)
            StatRow("Device RAM", "${kb(used)} / ${kb(m.deviceTotalRamKb)} used", ff, fs, lc, vc)
        }
    }
}

@Composable
private fun GpuSection(g: GpuStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("GPU & Frames", ff, fs) {
        g.jankPercent?.let {
            PercentBar("Janky frames", it, 100f, ff, fs, lc, vc, warnAbove = 5f, badAbove = 20f)
        }
        StatRow("Frames rendered", g.totalFrames?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Janky frames", g.jankyFrames?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("50/90/95/99th", ms4(g.p50Ms, g.p90Ms, g.p95Ms, g.p99Ms), ff, fs, lc, vc)
        StatRow("Missed vsync", g.missedVsync?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Slow UI thread", g.slowUiThread?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Slow draw cmds", g.slowDrawCommands?.toString() ?: "—", ff, fs, lc, vc)
        g.gpuBusyPercent?.let { StatRow("GPU busy (device)", pct(it), ff, fs, lc, vc) }
    }
}

@Composable
private fun NetworkSection(n: NetworkStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("Network", ff, fs) {
        StatRow("Down / Up now", "${rate(n.rxBytesPerSec)} / ${rate(n.txBytesPerSec)}", ff, fs, lc, vc, emphasize = true)
        StatRow("Received (24h)", bytes(n.totalRxBytes), ff, fs, lc, vc)
        StatRow("Sent (24h)", bytes(n.totalTxBytes), ff, fs, lc, vc)
        StatRow("Wi-Fi / Mobile", "${bytes(n.wifiBytes)} / ${bytes(n.mobileBytes)}", ff, fs, lc, vc)
    }
}

@Composable
private fun PowerSection(p: PowerStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("Power & Scheduling", ff, fs) {
        StatRow("Wakelocks held", p.wakelockCount?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Partial wakelock time", p.partialWakelockMs?.let { ms(it) } ?: "—", ff, fs, lc, vc)
        StatRow("Jobs registered", p.jobsRegistered?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Alarms registered", p.alarmsRegistered?.toString() ?: "—", ff, fs, lc, vc)
        val batt = p.batteryLevelPercent?.let { "$it%${if (p.charging == true) " ⚡" else ""}" } ?: "—"
        StatRow("Battery", batt, ff, fs, lc, vc)
        StatRow("Battery temp", p.batteryTempC?.let { "${"%.1f".format(it)}°C" } ?: "—", ff, fs, lc, vc)
    }
}

@Composable
private fun HealthSection(h: HealthStats, ff: FontFamily?, fs: Int, lc: Color, vc: Color) {
    StatCard("Process Health", ff, fs) {
        StatRow("Threads", h.threadCount?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Open file descriptors", h.fdCount?.toString() ?: "—", ff, fs, lc, vc)
        StatRow("Disk read / write now", "${rate(h.ioReadBytesPerSec)} / ${rate(h.ioWriteBytesPerSec)}", ff, fs, lc, vc, emphasize = true)
        StatRow("Disk read total", bytes(h.totalIoReadBytes), ff, fs, lc, vc)
        StatRow("Disk write total", bytes(h.totalIoWriteBytes), ff, fs, lc, vc)
    }
}

// ----------------------------------------------------------------------------------------------
// Reusable pieces
// ----------------------------------------------------------------------------------------------

@Composable
private fun StatCard(title: String, ff: FontFamily?, fs: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, color = Color.White, fontSize = (fs + 1).sp, fontWeight = FontWeight.Bold, fontFamily = ff)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun StatRow(
    label: String, value: String, ff: FontFamily?, fs: Int, lc: Color, vc: Color,
    emphasize: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = lc, fontSize = (fs - 1).sp, fontFamily = ff, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = vc, fontSize = (if (emphasize) fs + 1 else fs - 1).sp, fontFamily = ff,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PercentBar(
    label: String, value: Float, max: Float, ff: FontFamily?, fs: Int, lc: Color, vc: Color,
    warnAbove: Float = 60f, badAbove: Float = 85f,
) {
    val fraction = (value / max).coerceIn(0f, 1f)
    val barColor = when {
        value >= badAbove -> Color(0xFFE53935)
        value >= warnAbove -> Color(0xFFFFB300)
        else -> Color(0xFF43A047)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = lc, fontSize = (fs - 1).sp, fontFamily = ff, modifier = Modifier.weight(1f))
            Text(pct(value), color = vc, fontSize = (fs - 1).sp, fontFamily = ff, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun NoteBanner(note: String, ff: FontFamily?, fs: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFFB300).copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", color = Color(0xFFFFB300), fontSize = (fs).sp, fontFamily = ff)
        Spacer(Modifier.width(8.dp))
        Text(note, color = Color.White.copy(alpha = 0.85f), fontSize = (fs - 2).sp, fontFamily = ff)
    }
}

// ----------------------------------------------------------------------------------------------
// Formatting
// ----------------------------------------------------------------------------------------------

private fun pct(v: Float?): String = if (v == null) "—" else "${"%.1f".format(v)}%"

private fun kb(v: Long?): String = if (v == null) "—" else bytes(v * 1024)

private fun bytes(v: Long?): String {
    if (v == null) return "—"
    if (v < 1024) return "$v B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = v.toDouble() / 1024
    var i = 0
    while (size >= 1024 && i < units.size - 1) { size /= 1024; i++ }
    return "${"%.1f".format(size)} ${units[i]}"
}

private fun rate(v: Long?): String = if (v == null) "—" else "${bytes(v)}/s"

private fun ms(v: Long): String {
    if (v < 1000) return "${v}ms"
    val s = v / 1000
    if (s < 60) return "${s}s"
    val m = s / 60
    if (m < 60) return "${m}m ${s % 60}s"
    return "${m / 60}h ${m % 60}m"
}

private fun ms4(a: Float?, b: Float?, c: Float?, d: Float?): String {
    if (a == null && b == null && c == null && d == null) return "—"
    fun f(x: Float?) = x?.let { "${it.toInt()}" } ?: "—"
    return "${f(a)}/${f(b)}/${f(c)}/${f(d)} ms"
}

/** Stable-ish accent color for a library label so the eye can group threads by library. */
private fun accentFor(library: String): Color {
    val palette = listOf(
        Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFBA68C8),
        Color(0xFF4DD0E1), Color(0xFFE57373), Color(0xFFA1887F), Color(0xFF90A4AE),
    )
    val idx = (library.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}
