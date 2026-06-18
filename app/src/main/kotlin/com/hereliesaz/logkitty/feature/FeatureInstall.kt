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
 * state. On a build installed outside Play (e.g. the fused GitHub APK) the module is already present,
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
                .addOnFailureListener {
                    status = FeatureInstallStatus.Failed(it.message ?: "Install failed")
                }
        }
    }
}
