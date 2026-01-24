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
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
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

    // Track interaction to prevent premature shrinking
    private var isInteractingRaw = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY -> collapseBottomSheet()
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

        if (Settings.canDrawOverlays(this)) {
            setupBottomSheetOverlay()
        }

        val filter = IntentFilter().apply {
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

        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

        composeView = ComposeView(this).apply {
            // FIX: Raw Touch Listener to capture drag start even in small window
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isInteractingRaw = true
                    expandWindowForInteraction()
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    isInteractingRaw = false
                }
                false // Allow event to propagate to Compose
            }

            setContent {
                val density = LocalDensity.current
                val fontSizeSp by viewModel.fontSize.collectAsState()
                
                val screenHeightPx = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        windowManager.currentWindowMetrics.bounds.height()
                    } else {
                        @Suppress("DEPRECATION")
                        resources.displayMetrics.heightPixels
                    }
                }
                val screenHeight = (screenHeightPx / density.density).dp
                val navBarHeight = with(density) { navBarHeightPx.toDp() }

                // --- HEIGHT CALCULATION ---
                // We want ONE line of text + Padding + Navbar.
                // 1.5 multiplier for line height + spacing
                // 16.dp for container padding
                val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
                val contentHeightPx = (fontSizePx * 1.5f) + with(density) { 16.dp.toPx() }
                
                // Total height = Content sitting above navbar + Navbar itself
                val collapsedTotalHeightPx = (contentHeightPx + navBarHeightPx).toInt()
                val collapsedHeightDp = with(density) { collapsedTotalHeightPx.toDp() }

                val sheetState = rememberBottomSheetState(
                    initialValue = BottomSheetValue.Collapsed
                )

                DisposableEffect(sheetState) {
                    bottomSheetState = sheetState
                    onDispose { bottomSheetState = null }
                }

                val coroutineScope = rememberCoroutineScope()
                var delayedShrinkJob by remember { mutableStateOf<Job?>(null) }
                var currentPeekFraction by remember { mutableStateOf(0.25f) }

                // Sync window size with sheet state
                val syncWindowHeight = {
                     if (!isInteractingRaw) {
                         val params = composeView?.layoutParams as? WindowManager.LayoutParams
                         if (params != null) {
                             delayedShrinkJob?.cancel()
                             delayedShrinkJob = coroutineScope.launch {
                                 delay(250) // Small buffer
                                 
                                 if (isInteractingRaw) return@launch

                                 val targetHeightPx = when (sheetState.value) {
                                     BottomSheetValue.Collapsed -> collapsedTotalHeightPx
                                     BottomSheetValue.Peeked -> (screenHeightPx * currentPeekFraction + navBarHeightPx).toInt()
                                     // Expanded needs full screen to allow interactions everywhere if needed, 
                                     // or just large enough. MATCH_PARENT covers safety.
                                     BottomSheetValue.Expanded -> WindowManager.LayoutParams.MATCH_PARENT
                                 }

                                 if (params.height != targetHeightPx) {
                                     params.height = targetHeightPx
                                     params.y = 0 
                                     // Ensure we draw behind navbar
                                     params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                     
                                     // Manage touch pass-through
                                     // If we are collapsed/peeked, we technically only "need" touches on the sheet,
                                     // but since we resize the window TO the sheet size, we don't need FLAG_NOT_TOUCHABLE.
                                     // If expanded (MATCH_PARENT), we might want pass-through for empty areas, 
                                     // but bottom sheets usually cover everything.
                                     params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

                                     try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
                                 }
                             }
                         }
                     }
                }

                LaunchedEffect(sheetState.value) {
                    syncWindowHeight()
                }

                LogKittyTheme {
                    LogBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        screenHeight = screenHeight,
                        navBarHeight = navBarHeight,
                        collapsedHeightDp = collapsedHeightDp,
                        currentPeekFraction = currentPeekFraction,
                        onPeekFractionChange = { currentPeekFraction = it },
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

        // Initial setup
        val screenHeightPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
        val initialHeight = (screenHeightPx * 0.05f + navBarHeightPx).toInt() // Safe default

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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

    private fun expandWindowForInteraction() {
        val params = composeView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
            params.height = WindowManager.LayoutParams.MATCH_PARENT
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
