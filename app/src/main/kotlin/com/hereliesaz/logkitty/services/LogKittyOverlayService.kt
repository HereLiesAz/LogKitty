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
import androidx.core.content.ContextCompat
import com.hereliesaz.aznavrail.bottomsheet.AzBottomSheetWindowHost
import com.hereliesaz.aznavrail.bottomsheet.AzSheetController
import com.hereliesaz.aznavrail.model.AzSheetConfig
import com.hereliesaz.aznavrail.model.AzSheetDetent
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.MainApplication
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.MainViewModel
import com.hereliesaz.logkitty.ui.hide
import com.hereliesaz.logkitty.core.AccessibilityActions
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme
import com.hereliesaz.logkitty.utils.ComposeLifecycleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * [LogKittyOverlayService] hosts the always-on Compose overlay using AzNavRail's
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
 *   - Running the Context Mode foreground monitor and reacting to the launcher (overlay passthrough).
 */
class LogKittyOverlayService : Service() {

    private var sheetHost: AzBottomSheetWindowHost? = null
    private var owners: ComposeLifecycleHelper? = null
    private val controller = AzSheetController(initial = AzSheetDetent.HIDDEN)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // True once startForeground() has succeeded. If the OS refuses the (background) foreground-service
    // start, we never flip this and tear the service down instead of crashing or lingering.
    private var startedForeground = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AccessibilityActions.ACTION_COLLAPSE_OVERLAY) {
                controller.hide()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If onCreate() could not promote us to a foreground service (e.g. the OS refused a
        // background sticky-restart on Android 12+), don't keep getting restarted — bail.
        if (!startedForeground) {
            stopSelf()
            return START_NOT_STICKY
        }
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
            ACTION_TOGGLE_PAUSE -> {
                // The isPaused collector in onCreate refreshes the notification.
                (applicationContext as MainApplication).mainViewModel.togglePause()
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()

        if (!promoteToForeground(notification)) {
            // The OS refused the foreground-service start (e.g. a background sticky-restart on
            // Android 12+). Bail cleanly instead of crashing or lingering as a started-but-not-
            // foreground service. onStartCommand will also short-circuit via startedForeground.
            stopSelf()
            return
        }
        startedForeground = true

        // Anything past the foreground promotion can fail (overlay setup, receiver registration);
        // a failure here must tear the service down, not leave a half-initialized foreground service.
        try {
            if (Settings.canDrawOverlays(this)) setupOverlay()

            val viewModel = (applicationContext as MainApplication).mainViewModel
            // Each new service session starts actively logging — the app-scoped ViewModel would
            // otherwise retain a stale paused state from a previous session.
            viewModel.setPaused(false)
            // Capture only runs while the overlay (the sole consumer of the live stream) is up.
            viewModel.startCapture()

            // Keep the notification's Start/Stop action in sync with the capture state, whether it's
            // toggled from the notification or the bottom sheet's play/pause control.
            serviceScope.launch {
                viewModel.isPaused.collect { updateNotification() }
            }

            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(AccessibilityActions.ACTION_COLLAPSE_OVERLAY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Overlay initialization failed; tearing down service", e)
            // Mark not-foregrounded so a subsequent onStartCommand short-circuits; onDestroy still
            // rolls back any capture that had already started (stopCapture is idempotent).
            startedForeground = false
            stopSelf()
        }
    }

    /**
     * Promotes the service to the foreground, returning `true` on success.
     *
     * On Android 14+ the call MUST pass [ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE] (the
     * service is declared `specialUse` in the manifest); the 2-arg overload is illegal there and was
     * the original crash. If the OS refuses the start outright — `ForegroundServiceStartNotAllowedException`
     * on Android 12+, thrown when a `START_STICKY` service is restarted in the background — we do NOT
     * retry (a second start would throw again) and simply report failure so the caller can stop.
     */
    private fun promoteToForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(SERVICE_ID, notification)
        }
        true
    } catch (e: Exception) {
        // Compare by class name so the (API 31+) exception type isn't referenced on API 30 devices.
        val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
        if (notAllowed) {
            android.util.Log.w(TAG, "Foreground start not allowed; stopping service", e)
        } else {
            android.util.Log.e(TAG, "Failed to start foreground service", e)
        }
        false
    }

    override fun onDestroy() {
        // Stop logcat capture / unregister the foreground receiver now that the overlay is gone.
        // stopCapture() is idempotent, so it's safe even if the service never fully started.
        (applicationContext as MainApplication).mainViewModel.stopCapture()
        serviceScope.cancel()
        sheetHost?.detach()
        sheetHost = null
        owners?.let {
            it.onStop()
            it.onDestroy()
        }
        owners = null
        try { unregisterReceiver(receiver) } catch (e: Exception) { e.printStackTrace() }
        // Call super last so our cleanup runs while the service Context is still fully valid.
        super.onDestroy()
    }

    private fun setupOverlay() {
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel
        // Use the *actual* navigation-bar inset so the overlay window is exactly content + nav bar,
        // and the PeekStrip lifts its text by this same measured value. The resource value is a
        // fixed ~48dp even on gesture nav, which would otherwise make the window taller than the bar
        // and leave a touch dead zone over the app below. `getInsetsIgnoringVisibility` reports the
        // bar height even while it's temporarily hidden.
        val navBarHeightPx = run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                wm.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars())
                    .bottom
            } else {
                val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            }
        }

        val composeOwners = ComposeLifecycleHelper().also {
            it.onCreate()
            it.onStart()
        }
        owners = composeOwners

        val host = AzBottomSheetWindowHost(
            context = this,
            controller = controller,
            config = sheetConfig(viewModel),
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
                    host.updateConfig(sheetConfig(viewModel))
                }
        }

        // While on the launcher, disable the overlay so the system swipe-up-for-app-drawer
        // gesture is untouched. Re-enable when any other foreground app comes up.
        serviceScope.launch {
            viewModel.currentForegroundApp.collect { pkg ->
                controller.isEnabled = !AccessibilityActions.isLauncherPackage(pkg)
            }
        }

        // Context Mode: run the foreground-app monitor while it's enabled. collectLatest cancels the
        // previous run when Context Mode or the root flag changes (and stops it when disabled).
        serviceScope.launch {
            combine(viewModel.isContextModeEnabled, viewModel.isRootEnabled) { ctx, root -> ctx to root }
                .collectLatest { (contextMode, useRoot) ->
                    if (contextMode) ForegroundAppMonitor(this@LogKittyOverlayService).observe(useRoot)
                }
        }
    }

    /**
     * Build an [AzSheetConfig] from the user's current settings.
     *
     * The overlay window is resized to exactly these heights by the library, so they directly
     * control how the HIDDEN/PEEK strips look. The nav bar is NOT folded in: the library's
     * nav-bar decor window tints the system nav bar separately, so the sheet sits above it — each
     * strip is sized to exactly its text lines, and the bottom line sits right above the nav bar.
     */
    private fun sheetConfig(viewModel: MainViewModel): AzSheetConfig {
        val density = resources.displayMetrics.density
        val fontSize = viewModel.fontSize.value
        // Matches the Text lineHeight used in LogBottomSheet's PeekStrip (fontSize * 1.35).
        val lineHeightPx = fontSize.toFloat() * density * 1.35f

        // Heights above the nav bar: one line at HIDDEN, three at PEEK. The host is given the
        // measured nav-bar inset so the window sits cleanly above the bar (drawBehindNavBar is off
        // because the overlay nav bar isn't actually see-through, which only cluttered the strips).
        val hiddenPx = (lineHeightPx * 1)
        val hiddenDp = (hiddenPx / density).dp

        val peekPx = (lineHeightPx * 3)
        val peekDp = (peekPx / density).dp
        return AzSheetConfig(
            backgroundColor = Color(viewModel.backgroundColor.value),
            backgroundAlpha = viewModel.overlayOpacity.value,
            peekDp = peekDp,
            hiddenStripDp = hiddenDp,
            halfFraction = 0.5f,
            fullFraction = 0.9f,
            collapseOnBack = true,
            // Horizontal swipe at the shell level is not wired by AzBottomSheetWindowHost; the
            // LogBottomSheet content handles tab swipes itself.
            horizontalSwipeEnabled = false,
            handleVisible = false,
            cornerRadiusDp = 0.dp,
            // Keep the sheet above the system nav bar. (Drawing behind a see-through bar needs an
            // AzNavRail host change; until then this just cluttered HIDDEN with extra lines.)
            drawBehindNavBar = false,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Rebuilds and re-posts the notification (e.g. to flip the Start/Stop action on pause toggle). */
    private fun updateNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(SERVICE_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val isPaused = (applicationContext as MainApplication).mainViewModel.isPaused.value

        // Tapping the body opens LogKitty; capture/teardown are driven by the explicit actions below.
        val openIntent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val openPending = PendingIntent.getActivity(this, 3, openIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, LogKittyOverlayService::class.java).apply { action = ACTION_STOP_SERVICE }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val settingsIntent = Intent(this, LogKittyOverlayService::class.java).apply { action = ACTION_OPEN_SETTINGS }
        val settingsPending = PendingIntent.getService(this, 1, settingsIntent, PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = Intent(this, LogKittyOverlayService::class.java).apply { action = ACTION_TOGGLE_PAUSE }
        val pausePending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                if (isPaused) getString(R.string.notif_text_paused) else getString(R.string.notif_text_running)
            )
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openPending)
            // Pause/Resume toggles log capture (mirrors the sheet's play/pause control).
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) getString(R.string.notif_action_resume) else getString(R.string.notif_action_pause),
                pausePending
            )
            // Stop tears down the overlay and the foreground service entirely.
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_action_stop), stopPending)
            .addAction(android.R.drawable.ic_menu_preferences, getString(R.string.notif_action_app_settings), settingsPending)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "LogKittyOverlay"
        private const val CHANNEL_ID = "logkitty_overlay_channel"
        private const val SERVICE_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.hereliesaz.logkitty.STOP_SERVICE"
        private const val ACTION_OPEN_SETTINGS = "com.hereliesaz.logkitty.OPEN_SETTINGS"
        private const val ACTION_TOGGLE_PAUSE = "com.hereliesaz.logkitty.TOGGLE_PAUSE"
    }
}
