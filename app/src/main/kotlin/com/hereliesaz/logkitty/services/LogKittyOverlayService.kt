package com.hereliesaz.logkitty.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.hereliesaz.aznavrail.bottomsheet.AzBottomSheetWindowHost
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzSheetConfig
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.MainApplication
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.MainViewModel
import com.hereliesaz.logkitty.ui.hide
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme
import com.hereliesaz.logkitty.utils.ComposeLifecycleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * [LogKittyOverlayService] hosts the always-on Compose overlay using AzNavRail 8.13's
 * [AzBottomSheetWindowHost]. The library owns:
 *
 *   - The `TYPE_APPLICATION_OVERLAY` window and the per-detent `WindowManager` resize bridge.
 *   - The accumulated-delta vertical drag, the scrim above HALF/FULL, and the hidden swipe strip.
 *   - The companion `TYPE_ACCESSIBILITY_OVERLAY` window that paints over the system nav bar so the
 *     bar's opaque background blends with the sheet color (attached via [AzBottomSheetWindowHost.attachNavBarDecor]).
 *
 * This Service retains responsibility for:
 *   - The foreground notification.
 *   - Launcher passthrough: while the launcher is foreground we flip [AzSheetController.isEnabled]
 *     so the window shrinks to a HIDDEN strip and the swipe-up-for-app-drawer gesture is untouched.
 *   - Reactive [AzSheetConfig] updates so the PEEK height and sheet color track the user's
 *     settings (font size, overlay opacity, background color).
 *   - The collapse-overlay broadcast from [LogKittyAccessibilityService] (home/recents handling).
 */
class LogKittyOverlayService : Service() {

    private var sheetHost: AzBottomSheetWindowHost? = null
    private var owners: ComposeLifecycleHelper? = null
    private val controller = AzSheetController(initial = AzSheetDetent.HIDDEN)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY) {
                controller.hide()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_SETTINGS -> {
                val settingsIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("EXTRA_SHOW_SETTINGS", true)
                }
                startActivity(settingsIntent)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(SERVICE_ID, notification)
            }
        } catch (e: Exception) {
            startForeground(SERVICE_ID, notification)
        }

        if (Settings.canDrawOverlays(this)) setupOverlay()

        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        sheetHost?.detach()
        sheetHost = null
        owners?.let {
            it.onStop()
            it.onDestroy()
        }
        owners = null
        try { unregisterReceiver(receiver) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupOverlay() {
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel
        val navBarHeightPx = run {
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        }

        val composeOwners = ComposeLifecycleHelper().also {
            it.onCreate()
            it.onStart()
        }
        owners = composeOwners

        val host = AzBottomSheetWindowHost(
            context = this,
            controller = controller,
            config = sheetConfig(viewModel, navBarHeightPx),
            lifecycleOwner = composeOwners,
            viewModelStoreOwner = composeOwners,
            savedStateRegistryOwner = composeOwners,
            navBarHeightPx = navBarHeightPx,
        ) {
            LogKittyTheme {
                LogBottomSheet(
                    controller = controller,
                    viewModel = viewModel,
                    onSaveClick = {
                        val intent = Intent(this@LogKittyOverlayService,
                            com.hereliesaz.logkitty.FileSaverActivity::class.java)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(intent)
                    },
                    onSettingsClick = {
                        val intent = Intent(this@LogKittyOverlayService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("EXTRA_SHOW_SETTINGS", true)
                        }
                        startActivity(intent)
                    },
                )
            }
        }
        sheetHost = host
        host.attach()

        // Nav-bar color sync requires a bound accessibility service; if the user hasn't granted it
        // the library will throw inside the attach call. Swallow that and fall back silently.
        runCatching { host.attachNavBarDecor() }

        // React to font size / overlay opacity / background color: rebuild the sheet config so the
        // PEEK strip height and sheet chrome track the user's settings live.
        serviceScope.launch {
            combine(
                viewModel.fontSize,
                viewModel.overlayOpacity,
                viewModel.backgroundColor,
            ) { fs, op, bg -> Triple(fs, op, bg) }
                .collect { (_, _, _) ->
                    host.updateConfig(sheetConfig(viewModel, navBarHeightPx))
                }
        }

        // While on the launcher, disable the overlay so the system swipe-up-for-app-drawer
        // gesture is untouched. Re-enable when any other foreground app comes up.
        serviceScope.launch {
            viewModel.currentForegroundApp.collect { pkg ->
                controller.isEnabled = !LogKittyAccessibilityService.isLauncherPackage(pkg)
            }
        }
    }

    /**
     * Build an [AzSheetConfig] from the user's current settings.
     *
     * `peekDp` is computed dynamically from the user's font size so the PEEK strip always fits one
     * line of the chosen size plus the system navigation bar's height (so the strip's content sits
     * above the nav bar rather than behind it).
     */
    private fun sheetConfig(viewModel: MainViewModel, navBarHeightPx: Int): AzSheetConfig {
        val density = resources.displayMetrics.density
        val fontSize = viewModel.fontSize.value
        val peekPx = (fontSize.toFloat() * density * 1.5f) + (24f * density) + navBarHeightPx
        val peekDp = (peekPx / density).dp
        return AzSheetConfig(
            backgroundColor = Color(viewModel.backgroundColor.value),
            backgroundAlpha = viewModel.overlayOpacity.value,
            peekDp = peekDp,
            hiddenStripDp = 14.dp,
            halfFraction = 0.5f,
            fullFraction = 0.9f,
            collapseOnBack = true,
            // Horizontal swipe at the shell level is not wired by AzBottomSheetWindowHost; the
            // LogBottomSheet content handles tab swipes itself.
            horizontalSwipeEnabled = false,
            handleVisible = false,
            cornerRadiusDp = 0.dp,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, LogKittyOverlayService::class.java).apply { action = ACTION_STOP_SERVICE }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val settingsIntent = Intent(this, LogKittyOverlayService::class.java).apply { action = ACTION_OPEN_SETTINGS }
        val settingsPending = PendingIntent.getService(this, 1, settingsIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LogKitty Running")
            .setContentText("Tap to Stop. Expand for Settings.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(stopPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPending)
            .addAction(android.R.drawable.ic_menu_preferences, "App Settings", settingsPending)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "logkitty_overlay_channel"
        private const val SERVICE_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.hereliesaz.logkitty.STOP_SERVICE"
        private const val ACTION_OPEN_SETTINGS = "com.hereliesaz.logkitty.OPEN_SETTINGS"
    }
}
