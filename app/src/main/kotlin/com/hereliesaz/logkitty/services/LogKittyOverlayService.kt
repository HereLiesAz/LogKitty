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
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.MainApplication
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.SheetController
import com.hereliesaz.logkitty.ui.SheetDetent
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme
import com.hereliesaz.logkitty.utils.ComposeLifecycleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [LogKittyOverlayService] hosts the always-on Compose overlay.
 *
 * **Touch confinement**
 * The WindowManager view is resized to match the current sheet detent so taps outside the sheet
 * fall through to the underlying application — even when the sheet is FULL height (the bottom 90%
 * of the screen) the window leaves the top 10% untouchable.
 *
 * **Back-button handling**
 * When the sheet is HALF or FULL the window is focusable, so the system delivers back events.
 * Pressing back collapses the sheet to HIDDEN, and the window immediately drops focus so a second
 * back is delivered to the underlying app.
 *
 * **Home / Recents**
 * The accessibility service broadcasts [LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY] with
 * a reason; this service collapses the sheet without performing any further action (the system
 * already handled the home/recents gesture).
 */
class LogKittyOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null

    private val controller = SheetController()
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        composeView?.let { view ->
            try {
                lifecycleHelper?.onStop()
                lifecycleHelper?.onDestroy()
                windowManager.removeView(view)
            } catch (e: Exception) { e.printStackTrace() }
        }
        try { unregisterReceiver(receiver) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupOverlay() {
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel
        val density = resources.displayMetrics.density
        val screenHeightPx = resources.displayMetrics.heightPixels

        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

        composeView = ComposeView(this).apply {
            // ComposeView is final in modern Compose, so we intercept back via an OnKeyListener
            // instead of subclassing. The listener fires only when the window is focusable
            // (HALF/FULL detent), which is exactly when we want to capture back.
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (onBack()) return@setOnKeyListener true
                }
                false
            }
            setContent {
                val composeDensity = LocalDensity.current
                val fontSizeInt by viewModel.fontSize.collectAsState()
                val fontSpPx = with(composeDensity) { fontSizeInt.sp.toPx() }
                val paddingPx = with(composeDensity) { 24.dp.toPx() }
                val peekContentPx = (fontSpPx * 1.5f) + paddingPx
                val peekTotalPx = (peekContentPx + navBarHeightPx).toInt()
                val peekDp = with(composeDensity) { peekTotalPx.toDp() }
                val navBarDp = with(composeDensity) { navBarHeightPx.toDp() }
                val screenHeightDp = with(composeDensity) { screenHeightPx.toDp() }

                LogKittyTheme {
                    LogBottomSheet(
                        controller = controller,
                        viewModel = viewModel,
                        screenHeight = screenHeightDp,
                        navBarHeight = navBarDp,
                        collapsedHeightDp = peekDp,
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
        }
        lifecycleHelper = ComposeLifecycleHelper(composeView!!)
        lifecycleHelper!!.onCreate()
        lifecycleHelper!!.onStart()

        // Initial layout params: position at bottom, height matched to PEEK detent.
        val initialHeight = peekHeightPx(density, navBarHeightPx, viewModel.fontSize.value)
        val params = baseLayoutParams(initialHeight, focusable = false)
        try { windowManager.addView(composeView, params) }
        catch (e: Exception) { e.printStackTrace() }

        // React to detent changes: resize the window, toggle focusability for back support.
        serviceScope.launch {
            controller.detentFlow.collect { detent ->
                val fontSize = viewModel.fontSize.value
                val targetHeight = heightForDetent(detent, density, navBarHeightPx, fontSize, screenHeightPx)
                val needsFocus = detent == SheetDetent.HALF || detent == SheetDetent.FULL
                updateWindow(targetHeight, focusable = needsFocus)
            }
        }
    }

    /** Sheet height in pixels for the given detent. Mirrors the heights in [LogBottomSheet]. */
    private fun heightForDetent(
        detent: SheetDetent,
        density: Float,
        navBarHeightPx: Int,
        fontSize: Int,
        screenHeightPx: Int,
    ): Int = when (detent) {
        SheetDetent.HIDDEN -> (14f * density).toInt().coerceAtLeast(1)
        SheetDetent.PEEK -> peekHeightPx(density, navBarHeightPx, fontSize)
        SheetDetent.HALF -> (screenHeightPx * 0.5f).toInt()
        SheetDetent.FULL -> (screenHeightPx * 0.9f).toInt()
    }

    private fun peekHeightPx(density: Float, navBarHeightPx: Int, fontSize: Int): Int {
        val fontPx = fontSize.toFloat() * density
        val paddingPx = 24f * density
        return ((fontPx * 1.5f) + paddingPx + navBarHeightPx).toInt()
    }

    private fun baseLayoutParams(heightPx: Int, focusable: Boolean): WindowManager.LayoutParams {
        // FLAG_NOT_TOUCH_MODAL is the key flag for passthrough — without it, any touch on the
        // screen targets this window even outside its drawn bounds. With it, only touches inside
        // the window's bounds reach us; everything else continues to the underlying app.
        val flagsBase = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val flags = if (focusable) flagsBase
        else flagsBase or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }
    }

    private fun updateWindow(heightPx: Int, focusable: Boolean) {
        val view = composeView ?: return
        if (!view.isAttachedToWindow) return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.height = heightPx
        params.flags = baseLayoutParams(heightPx, focusable).flags
        try { windowManager.updateViewLayout(view, params) }
        catch (e: Exception) { e.printStackTrace() }
    }

    /** Intercepts back-button so HALF/FULL collapses to HIDDEN before the app gets it. */
    private fun onBack(): Boolean {
        return when (controller.detent) {
            SheetDetent.HALF, SheetDetent.FULL -> { controller.hide(); true }
            else -> false
        }
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
