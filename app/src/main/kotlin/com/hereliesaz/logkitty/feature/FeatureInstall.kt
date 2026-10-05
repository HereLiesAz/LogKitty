package com.hereliesaz.logkitty.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

/** UI-facing status of an on-demand feature module. */
sealed interface FeatureInstallStatus {
    data object Installed : FeatureInstallStatus
    data object NotInstalled : FeatureInstallStatus
    /** [progress] is 0f..1f, or -1f when the total size isn't known yet. */
    data class Installing(val progress: Float) : FeatureInstallStatus
    data class Failed(val message: String) : FeatureInstallStatus
}

/** Current [status] of a module plus an [install] trigger to request it. */
class FeatureInstallHandle(
    val status: FeatureInstallStatus,
    val install: () -> Unit,
)

/**
 * Observes and drives installation of the dynamic feature module [moduleName] via Play's
 * [com.google.android.play.core.splitinstall.SplitInstallManager], surfacing progress as Compose
 * state. On a build installed outside Play (e.g. the fused sideload APK) the module is already present,
 * so this reports [FeatureInstallStatus.Installed] immediately.
 */
@Composable
fun rememberFeatureInstall(moduleName: String): FeatureInstallHandle {
    val context = LocalContext.current
    val manager = remember { SplitInstallManagerFactory.create(context.applicationContext) }

    var status by remember(moduleName) {
        mutableStateOf<FeatureInstallStatus>(
            if (manager.installedModules.contains(moduleName)) FeatureInstallStatus.Installed
            else FeatureInstallStatus.NotInstalled
        )
    }

    DisposableEffect(moduleName) {
        val listener = SplitInstallStateUpdatedListener { state ->
            if (!state.moduleNames().contains(moduleName)) return@SplitInstallStateUpdatedListener
            status = when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING -> {
                    val total = state.totalBytesToDownload()
                    val done = state.bytesDownloaded()
                    FeatureInstallStatus.Installing(if (total > 0) done.toFloat() / total else -1f)
                }
                SplitInstallSessionStatus.PENDING,
                SplitInstallSessionStatus.DOWNLOADED,
                SplitInstallSessionStatus.INSTALLING -> FeatureInstallStatus.Installing(-1f)
                SplitInstallSessionStatus.INSTALLED -> {
                    SplitCompat.install(context)
                    FeatureInstallStatus.Installed
                }
                SplitInstallSessionStatus.FAILED ->
                    FeatureInstallStatus.Failed("Install failed (code ${state.errorCode()})")
                // Large module or metered network: Play needs the user's OK, which needs an Activity.
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    val activity = context.findActivity()
                    if (activity != null) {
                        manager.startConfirmationDialogForResult(state, activity, CONFIRMATION_REQUEST_CODE)
                        FeatureInstallStatus.Installing(-1f)
                    } else {
                        FeatureInstallStatus.Failed("Open LogKitty to confirm the download")
                    }
                }
                // Cancelled (by the user or the confirmation dialog): back to the Get button.
                SplitInstallSessionStatus.CANCELING,
                SplitInstallSessionStatus.CANCELED -> FeatureInstallStatus.NotInstalled
                else -> status
            }
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    return remember(moduleName, status) {
        FeatureInstallHandle(status) {
            if (status is FeatureInstallStatus.Installed || status is FeatureInstallStatus.Installing) {
                return@FeatureInstallHandle
            }
            status = FeatureInstallStatus.Installing(-1f)
            val request = SplitInstallRequest.newBuilder().addModule(moduleName).build()
            manager.startInstall(request)
                // Session id 0 = already installed; no state updates will follow.
                .addOnSuccessListener { sessionId -> if (sessionId == 0) status = FeatureInstallStatus.Installed }
                .addOnFailureListener {
                    status = FeatureInstallStatus.Failed(it.message ?: "Install failed")
                }
        }
    }
}

private const val CONFIRMATION_REQUEST_CODE = 0x5717

/** Unwraps ContextWrappers to the hosting Activity, or null (e.g. the overlay's window context). */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
