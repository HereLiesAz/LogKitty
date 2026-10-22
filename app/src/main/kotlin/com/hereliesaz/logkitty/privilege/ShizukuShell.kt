package com.hereliesaz.logkitty.privilege

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Thin wrapper over the [Shizuku] API: availability, the permission handshake, and running a shell
 * snippet as Shizuku's `shell` (ADB-level) user.
 *
 * Shizuku 13 made `newProcess` private in favour of bound user services. A one-shot `pm grant`
 * doesn't justify an AIDL service, so the method is reached reflectively (the API's own docs note
 * this remains supported); `proguard-rules.pro` keeps it from being renamed.
 */
object ShizukuShell {

    enum class Status { NOT_RUNNING, UNSUPPORTED, NO_PERMISSION, READY }

    private const val REQUEST_CODE = 0x10C4

    /**
     * Like [status], but gives the Shizuku binder up to [waitMs] to arrive: ShizukuProvider delivers
     * it asynchronously after process start, so an early check would wrongly report NOT_RUNNING.
     */
    suspend fun awaitStatus(waitMs: Long = 2000): Status {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return status()
        withTimeoutOrNull(waitMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                val listener = Shizuku.OnBinderReceivedListener { if (cont.isActive) cont.resume(Unit) }
                Shizuku.addBinderReceivedListenerSticky(listener)
                cont.invokeOnCancellation { Shizuku.removeBinderReceivedListener(listener) }
            }
        }
        return status()
    }

    fun status(): Status = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> Status.NOT_RUNNING
        Shizuku.isPreV11() -> Status.UNSUPPORTED
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NO_PERMISSION
        else -> Status.READY
    }

    /** User-facing reason Shizuku can't be used right now, or null when it is ready. */
    fun failureMessage(status: Status): Int? = when (status) {
        Status.NOT_RUNNING -> com.hereliesaz.logkitty.R.string.shizuku_not_running
        Status.UNSUPPORTED -> com.hereliesaz.logkitty.R.string.shizuku_too_old
        Status.NO_PERMISSION -> com.hereliesaz.logkitty.R.string.shizuku_denied
        Status.READY -> null
    }

    /** Shows Shizuku's own consent dialog; resumes with whether access was granted. */
    suspend fun requestPermission(): Boolean {
        when (awaitStatus()) {
            Status.READY -> return true
            Status.NO_PERMISSION -> Unit
            else -> return false
        }
        return suspendCancellableCoroutine { cont ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (cont.isActive) cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    /** Runs [script] with `sh -c` as Shizuku's user; returns combined output, or null on failure/timeout. */
    suspend fun run(script: String, timeoutMs: Long = 5000): String? = withContext(Dispatchers.IO) {
        if (status() != Status.READY) return@withContext null
        val process = runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }
            method.invoke(null, arrayOf("sh", "-c", "$script 2>&1"), null, null) as Process
        }.getOrNull() ?: return@withContext null
        // The read blocks until the process exits, so a watchdog destroys a hung process to unblock it.
        coroutineScope {
            val timedOut = AtomicBoolean(false)
            val watchdog = launch {
                delay(timeoutMs)
                timedOut.set(true)
                process.destroy()
            }
            try {
                val out = process.inputStream.bufferedReader().readText()
                process.waitFor()
                if (timedOut.get()) null else out
            } catch (e: Exception) {
                null
            } finally {
                watchdog.cancel()
                process.destroy()
            }
        }
    }
}
