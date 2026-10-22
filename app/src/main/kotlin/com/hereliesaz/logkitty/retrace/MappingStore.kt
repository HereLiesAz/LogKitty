package com.hereliesaz.logkitty.retrace

import android.content.Context
import android.net.Uri
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

/**
 * Per-app R8/ProGuard mapping files, copied into `files/mappings/<package>.txt` and parsed into
 * memory. [retrace] is called for every incoming log line, so it does no I/O: lines are matched to
 * an app by UID (cached) and only apps with a loaded mapping cost anything.
 */
class MappingStore(context: Context, scope: CoroutineScope) {

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "mappings")
    private val loaded = ConcurrentHashMap<String, MappingFile>()
    private val uidPackages = ConcurrentHashMap<Int, Array<String>>()

    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    /** Packages that currently have a mapping. */
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            dir.listFiles { f -> f.name.endsWith(SUFFIX) }?.forEach { load(it.name.removeSuffix(SUFFIX), it) }
        }
    }

    /** Copies the mapping at [uri] in for [pkg] and parses it. Returns false if it holds no classes. */
    suspend fun import(pkg: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, pkg + SUFFIX)
        val tmp = File(dir, "$pkg.tmp")
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return@withContext false
            val parsed = tmp.bufferedReader().use { MappingFile.parse(it) }
            if (parsed.size == 0) { tmp.delete(); return@withContext false }
            if (!tmp.renameTo(target)) { tmp.delete(); return@withContext false }
            loaded[pkg] = parsed
            _packages.value = loaded.keys.toSet()
            true
        }.getOrElse {
            Log.w(TAG, "Mapping import failed for $pkg", it)
            tmp.delete()
            false
        }
    }

    fun remove(pkg: String) {
        loaded.remove(pkg)
        File(dir, pkg + SUFFIX).delete()
        _packages.value = loaded.keys.toSet()
    }

    /** Retraced replacement lines for a log line from [uid], or null to keep the line as is. */
    fun retrace(uid: Int?, line: String): List<String>? {
        if (loaded.isEmpty() || uid == null) return null
        // Empty lookups aren't cached: a transient miss must not disable retracing for the session.
        val pkgs = uidPackages[uid] ?: app.packageManager.getPackagesForUid(uid)?.also { uidPackages[uid] = it } ?: return null
        for (pkg in pkgs) {
            val mapping = loaded[pkg] ?: continue
            Retracer.retrace(line, mapping)?.let { return it }
        }
        return null
    }

    private fun load(pkg: String, file: File) {
        runCatching { file.bufferedReader().use { MappingFile.parse(it) } }
            // putIfAbsent: an import that finished first holds the newer mapping.
            .onSuccess { loaded.putIfAbsent(pkg, it); _packages.value = loaded.keys.toSet() }
            .onFailure { Log.w(TAG, "Could not parse mapping for $pkg", it) }
    }

    private companion object {
        const val TAG = "MappingStore"
        const val SUFFIX = ".txt"
    }
}
