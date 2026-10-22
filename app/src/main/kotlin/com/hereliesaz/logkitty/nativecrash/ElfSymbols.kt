package com.hereliesaz.logkitty.nativecrash

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Function symbols of one ELF shared library, for turning a tombstone's `pc` into `name+offset`.
 *
 * Reads `.symtab` (present in unstripped / `.so.dbg` builds — includes static functions) and falls
 * back to `.dynsym` (exports only). Tombstone `pc` values for a library are already ELF-relative
 * virtual addresses, so they are looked up directly. Names are left mangled; line numbers (DWARF)
 * are out of scope.
 */
class ElfSymbols private constructor(
    private val starts: LongArray,
    private val ends: LongArray,
    private val names: Array<String>,
    /** Hex GNU build-id, compared with the tombstone's `(BuildId: …)` to reject mismatched builds. */
    val buildId: String?,
) {
    val size get() = names.size

    /**
     * `name+0xoff` for the function containing [address], or null. The offset is reported relative
     * to [display] (the printed pc), so a pc-1 lookup still shows the familiar offset.
     */
    fun lookup(address: Long, display: Long = address): String? {
        val pc = address
        var lo = 0
        var hi = starts.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= pc) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (found < 0 || pc >= ends[found]) return null
        val off = display - starts[found]
        return if (off == 0L) names[found] else "${names[found]}+0x${off.toString(16)}"
    }

    companion object {
        private const val SHT_SYMTAB = 2
        private const val SHT_NOTE = 7
        private const val SHT_DYNSYM = 11
        private const val STT_FUNC = 2
        private const val STT_GNU_IFUNC = 10
        private const val NT_GNU_BUILD_ID = 3
        private const val EM_ARM = 40

        /** Parses [file]; returns null if it isn't a little-endian ELF with function symbols. */
        fun read(file: File): ElfSymbols? = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN)
                parse(buf)
            }
        }.getOrNull()

        internal fun parse(buf: ByteBuffer): ElfSymbols? {
            if (buf.limit() < 0x34 || buf.getInt(0) != 0x464c457f) return null // "\u007fELF"
            if (buf.get(5).toInt() != 1) return null // little-endian only (every Android ABI)
            val is64 = buf.get(4).toInt() == 2
            val machine = buf.getShort(0x12).toInt()
            val shoff = if (is64) buf.getLong(0x28) else buf.getInt(0x20).toUInt().toLong()
            val shentsize = buf.getShort(if (is64) 0x3A else 0x2E).toInt() and 0xffff
            val shnum = buf.getShort(if (is64) 0x3C else 0x30).toInt() and 0xffff

            data class Section(val type: Int, val offset: Long, val size: Long, val link: Int, val entsize: Long)
            val sections = (0 until shnum).map { i ->
                val b = (shoff + i.toLong() * shentsize).toInt()
                if (is64) Section(buf.getInt(b + 4), buf.getLong(b + 24), buf.getLong(b + 32), buf.getInt(b + 40), buf.getLong(b + 56))
                else Section(buf.getInt(b + 4), buf.getInt(b + 16).toUInt().toLong(), buf.getInt(b + 20).toUInt().toLong(), buf.getInt(b + 24), buf.getInt(b + 36).toUInt().toLong())
            }

            val buildId = sections.filter { it.type == SHT_NOTE }.firstNotNullOfOrNull { s -> readBuildId(buf, s.offset, s.size) }

            val table = sections.firstOrNull { it.type == SHT_SYMTAB && it.size > 0 }
                ?: sections.firstOrNull { it.type == SHT_DYNSYM && it.size > 0 }
                ?: return null
            val strtab = sections.getOrNull(table.link) ?: return null
            val entsize = if (table.entsize > 0) table.entsize else if (is64) 24L else 16L

            val syms = ArrayList<Triple<Long, Long, String>>()
            var off = table.offset
            while (off + entsize <= table.offset + table.size) {
                val b = off.toInt()
                val nameIdx: Int
                val info: Int
                var value: Long
                val size: Long
                if (is64) {
                    nameIdx = buf.getInt(b); info = buf.get(b + 4).toInt(); value = buf.getLong(b + 8); size = buf.getLong(b + 16)
                } else {
                    nameIdx = buf.getInt(b); value = buf.getInt(b + 4).toUInt().toLong(); size = buf.getInt(b + 8).toUInt().toLong(); info = buf.get(b + 12).toInt()
                }
                off += entsize
                val type = info and 0xf
                if ((type != STT_FUNC && type != STT_GNU_IFUNC) || value == 0L) continue
                if (machine == EM_ARM) value = value and 1L.inv() // Thumb bit
                val name = cString(buf, (strtab.offset + nameIdx).toInt()) ?: continue
                if (name.isNotEmpty()) syms += Triple(value, size, name)
            }
            if (syms.isEmpty()) return null
            syms.sortBy { it.first }
            val starts = LongArray(syms.size) { syms[it].first }
            // Size-less symbols run to the next symbol.
            val ends = LongArray(syms.size) { i ->
                val (start, size, _) = syms[i]
                if (size > 0) start + size else syms.getOrNull(i + 1)?.first ?: (start + 1)
            }
            return ElfSymbols(starts, ends, Array(syms.size) { syms[it].third }, buildId)
        }

        private fun readBuildId(buf: ByteBuffer, start: Long, size: Long): String? {
            var p = start.toInt()
            val end = (start + size).toInt()
            while (p + 12 <= end) {
                val namesz = buf.getInt(p); val descsz = buf.getInt(p + 4); val type = buf.getInt(p + 8)
                // Malformed notes must not loop forever or walk backwards.
                if (namesz < 0 || descsz < 0 || namesz > end || descsz > end) return null
                val name = p + 12
                val desc = name + ((namesz + 3) and 3.inv())
                if (desc + descsz > end) return null
                if (type == NT_GNU_BUILD_ID && namesz == 4 && cString(buf, name) == "GNU") {
                    return (0 until descsz).joinToString("") { "%02x".format(buf.get(desc + it)) }
                }
                p = desc + ((descsz + 3) and 3.inv())
            }
            return null
        }

        private fun cString(buf: ByteBuffer, start: Int): String? {
            if (start < 0 || start >= buf.limit()) return null
            var end = start
            while (end < buf.limit() && buf.get(end).toInt() != 0) end++
            val bytes = ByteArray(end - start)
            for (i in bytes.indices) bytes[i] = buf.get(start + i)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
