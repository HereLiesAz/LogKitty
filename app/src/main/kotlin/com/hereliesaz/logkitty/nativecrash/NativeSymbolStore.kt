package com.hereliesaz.logkitty.nativecrash

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Per-app native symbol files: `files/symbols/<package>/<abi>/<libname>.so`.
 *
 * Accepts loose `.so` files (stored under ABI `any`) or the `native-debug-symbols.zip` AGP produces
 * (`<abi>/libfoo.so.dbg` / `.so.sym` entries). Everything is parsed up front, so [symbolicate] —
 * called on the log ingest path — never touches the disk.
 */
class NativeSymbolStore(context: Context, scope: CoroutineScope) {

    private val app = context.applicationContext
    private val root = File(app.filesDir, "symbols")

    /** pkg → "abi/libname" → symbols. */
    private val loaded = ConcurrentHashMap<String, ConcurrentHashMap<String, ElfSymbols>>()

    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    /** Packages that currently have native symbols. */
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            root.listFiles()?.filter { it.isDirectory }?.forEach { pkgDir ->
                pkgDir.walkTopDown().filter { it.isFile }.forEach { load(pkgDir.name, it) }
            }
            publish()
        }
    }

    /** Imports [uris] for [pkg]; returns how many libraries with symbols were added. */
    suspend fun import(pkg: String, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var added = 0
        for (uri in uris) {
            val name = displayName(uri) ?: continue
            runCatching {
                if (name.endsWith(".zip", ignoreCase = true)) {
                    ZipInputStream(app.contentResolver.openInputStream(uri) ?: return@runCatching).use { zip ->
                        generateSequence { zip.nextEntry }.forEach { entry ->
                            val lib = libName(entry.name.substringAfterLast('/')) ?: return@forEach
                            val abi = entry.name.split('/').dropLast(1).lastOrNull { it in KNOWN_ABIS } ?: ANY
                            if (store(pkg, abi, lib) { out -> zip.copyTo(out) }) added++
                        }
                    }
                } else {
                    val lib = libName(name) ?: return@runCatching
                    if (store(pkg, ANY, lib) { out -> app.contentResolver.openInputStream(uri)?.use { it.copyTo(out) } }) added++
                }
            }.onFailure { Log.w(TAG, "Symbol import failed for $name", it) }
        }
        publish()
        added
    }

    fun remove(pkg: String) {
        loaded.remove(pkg)
        File(root, pkg).deleteRecursively()
        publish()
    }

    /**
     * For a crash-dump backtrace line, appends `[symbol+0xoff]` from the matching library, or
     * returns null to keep the line. Libraries are chosen by package (from the frame's install path),
     * ABI, and — when both sides have one — an exact build-id match.
     */
    fun symbolicate(line: String): String? {
        if (loaded.isEmpty()) return null
        val frame = NativeFrame.parse(line) ?: return null
        val symbol = lookup(frame) ?: return null
        if (frame.existingSymbol != null && frame.existingSymbol.substringBefore('+') == symbol.substringBefore('+')) return null
        return line.substring(0, frame.end) + " [" + symbol + "]" + line.substring(frame.end)
    }

    /** Symbol for [frame], or null. Also used to symbolicate tombstone files. */
    fun lookup(frame: NativeFrame): String? {
        val owners = loaded.keys.filter { frame.path.contains("/$it-") || frame.path.contains("/$it/") }
            .ifEmpty { loaded.keys.toList() }
        val abis = listOfNotNull(frame.abi) + Build.SUPPORTED_ABIS + ANY
        for (pkg in owners) {
            val libs = loaded[pkg] ?: continue
            for (abi in abis.distinct()) {
                val syms = libs["$abi/${frame.libName}"] ?: continue
                if (frame.buildId != null && syms.buildId != null && frame.buildId != syms.buildId) continue
                syms.lookup(frame.pc)?.let { return it }
            }
        }
        return null
    }

    private fun store(pkg: String, abi: String, lib: String, write: (java.io.OutputStream) -> Unit): Boolean {
        val target = File(root, "$pkg/$abi/$lib")
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.outputStream().use(write)
        val syms = ElfSymbols.read(tmp)
        if (syms == null) { tmp.delete(); return false }
        tmp.renameTo(target)
        loaded.getOrPut(pkg) { ConcurrentHashMap() }["$abi/$lib"] = syms
        return true
    }

    private fun load(pkg: String, file: File) {
        val abi = file.parentFile?.name ?: return
        ElfSymbols.read(file)?.let { loaded.getOrPut(pkg) { ConcurrentHashMap() }["$abi/${file.name}"] = it }
            ?: Log.w(TAG, "Unreadable symbol file ${file.path}")
    }

    private fun publish() {
        _packages.value = loaded.filterValues { it.isNotEmpty() }.keys.toSet()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private companion object {
        const val TAG = "NativeSymbolStore"
        const val ANY = "any"
        val KNOWN_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64")

        /** `libfoo.so`, `libfoo.so.dbg`, `libfoo.so.sym` → `libfoo.so`; anything else → null. */
        fun libName(fileName: String): String? {
            val base = fileName.removeSuffix(".dbg").removeSuffix(".sym")
            return base.takeIf { it.endsWith(".so") && !it.contains('/') }
        }
    }
}
