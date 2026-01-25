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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dokar.sheets.BottomSheetState
import com.dokar.sheets.BottomSheetValue
import com.dokar.sheets.rememberBottomSheetState
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.MainApplication
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme
import com.hereliesaz.logkitty.utils.ComposeLifecycleHelper
import kotlinx.coroutines.*

class LogKittyOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null
    private var bottomSheetState: BottomSheetState? = null

    private var isInteractingRaw = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY) {
                collapseBottomSheet()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OPEN_SETTINGS) {
            val settingsIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("EXTRA_SHOW_SETTINGS", true)
            }
            startActivity(settingsIntent)
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

        if (Settings.canDrawOverlays(this)) {
            setupBottomSheetOverlay()
        }

        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (composeView != null) {
            try {
                lifecycleHelper?.onStop()
                lifecycleHelper?.onDestroy()
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun collapseBottomSheet() {
         if (bottomSheetState != null) {
             CoroutineScope(Dispatchers.Main).launch {
                 try { bottomSheetState?.collapse() } catch (e: Exception) { e.printStackTrace() }
             }
         }
    }

    private fun setupBottomSheetOverlay() {
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel

        // Physical pixels of the Navbar
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

        composeView = ComposeView(this).apply {
            // CRITICAL: Disable system fitting so we draw BEHIND the navbar
            WindowCompat.setDecorFitsSystemWindows(
                (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.Window) ?: return@apply, 
                false
            )
            
            // Raw Touch Listener: instantly expand window on touch
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isInteractingRaw = true
                    setWindowToFullScreen(true) // Expand immediately
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    isInteractingRaw = false
                    // We don't shrink here; we let Compose state settle and shrink later
                }
                false // Propagate to Compose
            }

            setContent {
                val density = LocalDensity.current
                val fontSizeSp by viewModel.fontSize.collectAsState()
                
                // Calculate Collapsed Height: 1 Line + Padding + Navbar
                val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
                val contentHeightPx = (fontSizePx * 1.5f) + with(density) { 24.dp.toPx() }
                val collapsedTotalHeightPx = (contentHeightPx + navBarHeightPx).toInt()
                val collapsedHeightDp = with(density) { collapsedTotalHeightPx.toDp() }
                val navBarHeightDp = with(density) { navBarHeightPx.toDp() }

                val sheetState = rememberBottomSheetState(
                    initialValue = BottomSheetValue.Collapsed
                )

                DisposableEffect(sheetState) {
                    bottomSheetState = sheetState
                    onDispose { bottomSheetState = null }
                }

                // Window State Management
                LaunchedEffect(sheetState.value, isInteractingRaw) {
                    if (isInteractingRaw) return@LaunchedEffect // Don't shrink while touching

                    // If Collapsed, shrink window to strip. If Expanded/Peeked, keep Full Screen.
                    if (sheetState.value == BottomSheetValue.Collapsed) {
                        // Delay slightly to let animation finish visually
                        delay(300)
                        if (!isInteractingRaw) {
                            setWindowToCollapsed(collapsedTotalHeightPx)
                        }
                    } else {
                        setWindowToFullScreen(false) // False = don't force touchable yet, let flags decide
                    }
                }

                LogKittyTheme {
                    LogBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        screenHeight = with(density) { resources.displayMetrics.heightPixels.toDp() }, // Approximation for layout
                        navBarHeight = navBarHeightDp,
                        collapsedHeightDp = collapsedHeightDp,
                        onSaveClick = {
                            val intent = Intent(this@LogKittyOverlayService, com.hereliesaz.logkitty.FileSaverActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        },
                        onSettingsClick = {
                            val intent = Intent(this@LogKittyOverlayService, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.putExtra("EXTRA_SHOW_SETTINGS", true)
                            startActivity(intent)
                        }
                    )
                }
            }
        }

        lifecycleHelper = ComposeLifecycleHelper(composeView!!)
        lifecycleHelper!!.onCreate()
        lifecycleHelper!!.onStart()

        // Initial Window Params (Collapsed State)
        val initialHeight = (100 * resources.displayMetrics.density).toInt() // Safe start
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // DRAW BEHIND NAVBAR
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setWindowToFullScreen(forceTouchable: Boolean) {
        val params = composeView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() // Make touchable
            try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun setWindowToCollapsed(heightPx: Int) {
        val params = composeView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.height != heightPx) {
            params.height = heightPx
            // Even in collapsed, we want touches on the strip, so keep touchable
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() 
            try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, LogKittyOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = Intent(this, LogKittyOverlayService::class.java).apply {
            action = ACTION_OPEN_SETTINGS
        }
        val settingsPendingIntent = PendingIntent.getService(
            this, 1, settingsIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val icon = android.R.drawable.ic_menu_view

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LogKitty Running")
            .setContentText("Tap to Stop. Expand for Settings.")
            .setSmallIcon(icon)
            .setContentIntent(stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_preferences, "App Settings", settingsPendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ideaz_overlay_channel"
        private const val SERVICE_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.hereliesaz.logkitty.STOP_SERVICE"
        private const val ACTION_OPEN_SETTINGS = "com.hereliesaz.logkitty.OPEN_SETTINGS"
    }
}
