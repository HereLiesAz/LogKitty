package com.hereliesaz.logkitty

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme

/**
 * Full-screen log view opened when a monitored app crashes.
 *
 * A crash drops the user on the launcher, where the overlay sheet is deliberately disabled, so the
 * sheet can't be expanded there. This activity renders the same [LogBottomSheet] content pinned at
 * FULL, on the crashed app's tab. Collapsing the sheet (close button, Back) finishes the activity.
 */
class CrashLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = (application as MainApplication).mainViewModel
        intent.getStringExtra(EXTRA_PACKAGE)?.let { showCrash(it) }

        setContent {
            val controller = remember { AzSheetController(initial = AzSheetDetent.FULL) }
            val background by viewModel.backgroundColor.collectAsState()

            // Close / collapse leaves FULL/HALF — the screen has no smaller state, so just leave.
            LaunchedEffect(controller.detent) {
                if (controller.detent != AzSheetDetent.FULL && controller.detent != AzSheetDetent.HALF) finish()
            }

            LogKittyTheme {
                Box(Modifier.fillMaxSize().background(Color(background)).safeDrawingPadding()) {
                    LogBottomSheet(
                        controller = controller,
                        viewModel = viewModel,
                        onSaveClick = {
                            startActivity(Intent(this@CrashLogActivity, FileSaverActivity::class.java))
                        },
                        onSettingsClick = {
                            startActivity(Intent(this@CrashLogActivity, MainActivity::class.java)
                                .putExtra("EXTRA_SHOW_SETTINGS", true))
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_PACKAGE)?.let { showCrash(it) }
    }

    /** Switches to [pkg]'s tab and dismisses its now-redundant crash notification. */
    private fun showCrash(pkg: String) {
        (application as MainApplication).mainViewModel.selectAppTab(pkg)
        getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(com.hereliesaz.logkitty.services.LogKittyOverlayService.crashNotificationId(pkg))
    }

    companion object {
        const val EXTRA_PACKAGE = "com.hereliesaz.logkitty.EXTRA_CRASHED_PACKAGE"

        fun intent(context: Context, pkg: String): Intent =
            Intent(context, CrashLogActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
