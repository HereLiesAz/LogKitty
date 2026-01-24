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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.hereliesaz.logkitty.MainActivity
import com.hereliesaz.logkitty.MainApplication
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.ui.LogBottomSheet
import com.hereliesaz.logkitty.ui.inspection.OverlayView
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme
import com.hereliesaz.logkitty.utils.ComposeLifecycleHelper
import com.dokar.sheets.BottomSheetState
import com.dokar.sheets.BottomSheetValue
import com.dokar.sheets.rememberBottomSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class LogKittyOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var isOverlayAdded = false

    // Bottom Sheet Overlay
    private var composeView: ComposeView? = null
    private var lifecycleHelper: ComposeLifecycleHelper? = null
    private var bottomSheetState: BottomSheetState? = null // To control sheet externally

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.hereliesaz.logkitty.TOGGLE_SELECT_MODE" -> {
                    val enable = intent.getBooleanExtra("ENABLE", false)
                    handleSelectionMode(enable)
                }
                "com.hereliesaz.logkitty.HIGHLIGHT_RECT" -> {
                    val rect = if (Build.VERSION.SDK_INT >= 33) {
                         intent.getParcelableExtra("RECT", Rect::class.java)
                    } else {
                         @Suppress("DEPRECATION")
                         intent.getParcelableExtra("RECT")
                    }
                    if (rect != null) {
                        overlayView?.updateHighlight(rect)
                    } else {
                        overlayView?.clearHighlight()
                    }
                }
                "com.hereliesaz.logkitty.SHOW_UPDATE_POPUP" -> {
                    val prompt = intent.getStringExtra("PROMPT")
                    if (!prompt.isNullOrBlank()) {
                        copyToClipboard(prompt)
                    }
                    overlayView?.showUpdateSplash()
                }
                LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY -> {
                    collapseBottomSheet()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent != null) {
            if (intent.hasExtra("ENABLE")) {
                 val enable = intent.getBooleanExtra("ENABLE", false)
                 handleSelectionMode(enable)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                 if (Build.VERSION.SDK_INT >= 34) {
                     startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                 } else {
                     startForeground(SERVICE_ID, notification)
                 }
            } catch (e: Exception) {
                startForeground(SERVICE_ID, notification)
            }
        } else {
            startForeground(SERVICE_ID, notification)
        }

        if (Settings.canDrawOverlays(this)) {
            setupOverlay()
            setupBottomSheetOverlay()
        }

        val filter = IntentFilter().apply {
            addAction("com.hereliesaz.logkitty.TOGGLE_SELECT_MODE")
            addAction("com.hereliesaz.logkitty.HIGHLIGHT_RECT")
            addAction("com.hereliesaz.logkitty.SHOW_UPDATE_POPUP")
            addAction(LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isOverlayAdded && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                android.util.Log.e("LogKittyOverlay", "Failed to remove overlay view", e)
            }
        }
        if (composeView != null) {
            try {
                lifecycleHelper?.onStop()
                lifecycleHelper?.onDestroy()
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                android.util.Log.e("LogKittyOverlay", "Failed to remove compose view", e)
            }
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            android.util.Log.e("LogKittyOverlay", "Failed to unregister receiver", e)
        }
    }

    private fun collapseBottomSheet() {
         if (bottomSheetState != null) {
             CoroutineScope(Dispatchers.Main).launch {
                 try {
                     bottomSheetState?.collapse()
                 } catch (e: Exception) {
                     android.util.Log.e("LogKittyOverlay", "Failed to collapse bottom sheet", e)
                 }
             }
         }
    }

    private fun handleSelectionMode(enable: Boolean) {
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            setupOverlay()
        }
        overlayView?.setSelectionMode(enable)
        updateOverlayParams(enable)
    }

    private fun setupOverlay() {
        if (isOverlayAdded) return
        overlayView = OverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(overlayView, params)
            isOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBottomSheetOverlay() {
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel

        composeView = ComposeView(this).apply {
            setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                // Robust screen height calculation using RealMetrics or WindowMetrics
                val screenHeightPx = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        windowManager.currentWindowMetrics.bounds.height()
                    } else {
                        val metrics = DisplayMetrics()
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.getRealMetrics(metrics)
                        metrics.heightPixels
                    }
                }
                val screenHeight = (screenHeightPx / density.density).dp

                val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
                val navBarHeight = with(density) { navBarHeightPx.toDp() }

                val sheetState = rememberBottomSheetState(
                    initialValue = BottomSheetValue.Collapsed
                )

                // Expose state to service
                DisposableEffect(sheetState) {
                    bottomSheetState = sheetState
                    onDispose { bottomSheetState = null }
                }

                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                var delayedShrinkJob by remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
                var isWindowExpanded by remember { mutableStateOf(false) }

                // Fixed Anchor: 0 (Immovable at bottom)
                val anchorYPx = 0
                val expandedHeightPx = (screenHeightPx * 0.90f + navBarHeightPx).toInt()

                val updateWindowHeight = { isInteracting: Boolean ->
                     val params = composeView?.layoutParams as? WindowManager.LayoutParams
                     if (params != null) {
                         // Always ensure Y anchor is maintained and Gravity is BOTTOM
                         params.y = anchorYPx
                         params.gravity = Gravity.BOTTOM

                         if (isInteracting) {
                             delayedShrinkJob?.cancel()
                             delayedShrinkJob = null

                             // Expand window upwards to allow full interaction
                             if (params.height != expandedHeightPx) {
                                 params.height = expandedHeightPx
                                 try {
                                     windowManager.updateViewLayout(composeView, params)
                                 } catch (e: Exception) {
                                     e.printStackTrace()
                                 }
                             }
                         } else {
                             // Delay shrinking to allow animation to settle
                             delayedShrinkJob?.cancel()
                             delayedShrinkJob = coroutineScope.launch {
                                 delay(400) // Wait for settle

                                 // Determine current detent height
                                 val currentValue = sheetState.value
                                 val targetHeightPx = when (currentValue) {
                                     BottomSheetValue.Collapsed -> (screenHeightPx * 0.02f + navBarHeightPx).toInt()
                                     BottomSheetValue.Peeked -> (screenHeightPx * 0.25f + navBarHeightPx).toInt()
                                     BottomSheetValue.Expanded -> (screenHeightPx * 0.80f + navBarHeightPx).toInt()
                                 }

                                 // Check if we started interacting again during delay
                                 if (isActive) {
                                     isWindowExpanded = false
                                     val currentParams = composeView?.layoutParams as? WindowManager.LayoutParams
                                     if (currentParams != null && (currentParams.height != targetHeightPx)) {
                                         currentParams.height = targetHeightPx
                                         // Y anchor remains constant
                                         currentParams.y = anchorYPx
                                         currentParams.gravity = Gravity.BOTTOM
                                         try {
                                             windowManager.updateViewLayout(composeView, currentParams)
                                         } catch (e: Exception) {
                                             e.printStackTrace()
                                         }
                                         isWindowExpanded = false
                                     }
                                 }
                             }
                         }
                     }
                }

                // Monitor detent changes to trigger resize if needed (even without interaction)
                LaunchedEffect(sheetState.value) {
                    // Force a settled update if we are not currently interacting
                    updateWindowHeight(false)
                }

                // Update WindowManager flags based on sheet state to allow touch-through
                DisposableEffect(sheetState.value) {
                    updateWindowManagerFlags()
                    onDispose { }
                }

                val bottomPadding = if (isWindowExpanded) screenHeight * 0.10f else 0.dp

                LogKittyTheme {
                    LogBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        screenHeight = screenHeight,
                        navBarHeight = navBarHeight,
                        isWindowExpanded = isWindowExpanded,
                        bottomPadding = bottomPadding,
                        onSendPrompt = { viewModel.sendPrompt(it) },
                        onInteraction = { isInteracting ->
                            updateWindowHeight(isInteracting)
                        },
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

        // Initial params: Height = Hidden (2%) or Peek (25%)? Initial state is Collapsed (Hidden)
        val navBarResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (navBarResourceId > 0) resources.getDimensionPixelSize(navBarResourceId) else 0
        val initialHeight = (resources.displayMetrics.heightPixels * 0.02f + navBarHeightPx).toInt()
        val initialY = 0

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = initialY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWindowManagerFlags() {
        val view = composeView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return

        // Always allow touches but let them pass through outside
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        // Height is managed by updateWindowHeight

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayParams(isSelectMode: Boolean) {
        if (!isOverlayAdded || overlayView == null) {
            if (isSelectMode && Settings.canDrawOverlays(this)) {
                setupOverlay()
            } else {
                return
            }
        }

        val params = overlayView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isSelectMode) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LogKittyOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val icon = android.R.drawable.ic_menu_view

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LogKitty Overlay")
            .setContentText("Overlay is active")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Prompt", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val CHANNEL_ID = "ideaz_overlay_channel"
        private const val SERVICE_ID = 1001
        private const val ACTION_STOP_SERVICE = "com.hereliesaz.logkitty.STOP_SERVICE"
    }
}
