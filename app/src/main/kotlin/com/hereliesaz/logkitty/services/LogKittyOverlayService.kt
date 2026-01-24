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
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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

    // Tracking for the "Stuck" fix
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
                // Don't stop service, user might want to tweak while running
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

        // Get precise Navbar height
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

        composeView = ComposeView(this).apply {
            // FIX 1: Raw Touch Listener to pre-expand window
            // This catches the touch BEFORE Compose or the WindowManager clips it
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isInteractingRaw = true
                    expandWindowForInteraction()
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    isInteractingRaw = false
                    // We don't shrink immediately; we let the Compose state (LaunchedEffect) handle the shrink
                    // based on where the sheet settles.
                }
                false // Return false so Compose still receives the event
            }

            setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                
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

                // Logic to sync Window Height with Sheet State
                val syncWindowHeight = {
                     if (!isInteractingRaw) {
                         val params = composeView?.layoutParams as? WindowManager.LayoutParams
                         if (params != null) {
                             delayedShrinkJob?.cancel()
                             delayedShrinkJob = coroutineScope.launch {
                                 // Wait for spring animation to settle
                                 delay(350)
                                 
                                 // If user started touching again during delay, abort shrink
                                 if (isInteractingRaw) return@launch

                                 val targetHeightPx = when (sheetState.value) {
                                     // Ensure Collapsed is tall enough to be grabbable but not blocking.
                                     // We add navBarHeightPx so it sits BEHIND the navbar.
                                     BottomSheetValue.Collapsed -> (screenHeightPx * 0.05f + navBarHeightPx).toInt()
                                     BottomSheetValue.Peeked -> (screenHeightPx * currentPeekFraction + navBarHeightPx).toInt()
                                     BottomSheetValue.Expanded -> WindowManager.LayoutParams.MATCH_PARENT
                                 }

                                 if (params.height != targetHeightPx) {
                                     params.height = targetHeightPx
                                     params.y = 0 
                                     params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                     
                                     // If Expanded, allow touches everywhere. If not, pass through touches outside.
                                     if (sheetState.value == BottomSheetValue.Expanded) {
                                         params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                     } else {
                                         // In collapsed/peek, we want the window to be small (targetHeight),
                                         // so we DON'T need FLAG_NOT_TOUCHABLE because the window itself is small.
                                         params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                     }

                                     try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
                                 }
                             }
                         }
                     }
                }

                // Monitor state changes
                LaunchedEffect(sheetState.value) {
                    syncWindowHeight()
                }

                LogKittyTheme {
                    LogBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        screenHeight = screenHeight,
                        navBarHeight = navBarHeight,
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

        // Initial setup: Small window at bottom
        val screenHeightPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
        val initialHeight = (screenHeightPx * 0.05f + navBarHeightPx).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // FIX 2: Always No Limits
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0 // Anchored to physical bottom
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Called by the raw OnTouchListener
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
        // FIX 3: Tapping notification body stops service
        val stopIntent = Intent(this, LogKittyOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // FIX 4: "App Settings" Action
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
            .setContentIntent(stopPendingIntent) // Body tap = Stop
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
