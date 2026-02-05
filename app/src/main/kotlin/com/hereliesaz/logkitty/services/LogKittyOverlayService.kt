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

/**
 * [LogKittyOverlayService] is the core component of the application, responsible for rendering the
 * always-on overlay window.
 *
 * It manages:
 * 1. The System Overlay Window (`WindowManager`).
 * 2. The Jetpack Compose UI host (`ComposeView`).
 * 3. The complex interaction logic that allows touches to pass through the overlay when it is
 *    collapsed or "peeking", but captures them when expanded.
 * 4. The lifecycle synchronization between the Android Service component and the Compose world.
 *
 * This service runs as a Foreground Service to ensure the system does not kill it while the user
 * is debugging other applications.
 */
class LogKittyOverlayService : Service() {

    // System Window Manager to add/update/remove the overlay view.
    private lateinit var windowManager: WindowManager

    // The container for our Jetpack Compose UI.
    private var composeView: ComposeView? = null

    // A helper to provide LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner to Compose.
    // This is CRITICAL because Services do not naturally provide these, and Compose crashes without them.
    private var lifecycleHelper: ComposeLifecycleHelper? = null

    // State of the bottom sheet (Collapsed, Expanded, Peek).
    private var bottomSheetState: BottomSheetState? = null

    // Flag to track if the user is currently interacting with the raw view (touch down).
    // This allows us to expand the window bounds *immediately* upon touch, before Compose even processes the gesture.
    private var isInteractingRaw = false

    /**
     * Receiver for actions broadcast by the [LogKittyAccessibilityService].
     * Specifically used to auto-collapse the overlay when the user goes to the Home screen or Recents.
     */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY) {
                collapseBottomSheet()
            }
        }
    }

    /**
     * Binding is not supported. This service operates purely as a started service.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Handles startup commands.
     * Supports:
     * - [ACTION_STOP_SERVICE]: Gracefully shuts down the overlay.
     * - [ACTION_OPEN_SETTINGS]: Launches the main configuration activity.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OPEN_SETTINGS) {
            val settingsIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required when starting Activity from Service
                putExtra("EXTRA_SHOW_SETTINGS", true)
            }
            startActivity(settingsIntent)
        }
        // Restart the service if the system kills it (standard for always-on tools).
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Start as foreground service immediately to prevent ANR or system killing.
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // API 34+ requires declaring the specific foreground service type in manifest and code.
                startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(SERVICE_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback for older APIs or if specific permission logic fails slightly differently
            startForeground(SERVICE_ID, notification)
        }

        // Verify overlay permission before attempting to add the view to avoid crashes.
        if (Settings.canDrawOverlays(this)) {
            setupBottomSheetOverlay()
        }

        // Register the broadcast receiver to listen for "Go Home" events from the accessibility service.
        val filter = IntentFilter(LogKittyAccessibilityService.ACTION_COLLAPSE_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up the Compose view and WindowManager to prevent leaks.
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

    /**
     * Programmatically collapses the bottom sheet to its peek state.
     * Safe to call from background threads (logic dispatched to Main).
     */
    private fun collapseBottomSheet() {
         if (bottomSheetState != null) {
             CoroutineScope(Dispatchers.Main).launch {
                 try { bottomSheetState?.collapse() } catch (e: Exception) { e.printStackTrace() }
             }
         }
    }

    /**
     * Initializes the ComposeView, sets up the window parameters, and attaches it to the WindowManager.
     */
    private fun setupBottomSheetOverlay() {
        // Access the singleton ViewModel from the Application to share state with the Settings UI.
        val app = applicationContext as MainApplication
        val viewModel = app.mainViewModel

        // Retrieve the system navigation bar height to ensure we don't draw under it (or account for it).
        // We use getIdentifier because directly accessing window insets in a Service is complex/unreliable across APIs.
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

        composeView = ComposeView(this).apply {
            // CRITICAL: Raw Touch Listener
            // When the window is "Collapsed" or "Peeking", its height is small.
            // If the user starts a drag (swipe up), we must INSTANTLY expand the underlying WindowManager window
            // to MATCH_PARENT so the gesture has room to continue.
            // If we waited for Compose to tell us, the gesture would exit the window bounds and be lost.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    isInteractingRaw = true
                    setWindowToFullScreen(true) // Expand window bounds immediately
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    isInteractingRaw = false
                    // We don't collapse here immediately; we let the LaunchedEffect below handle the settling logic.
                }
                false // Return false to let the event propagate to the Compose layer for actual UI handling.
            }

            setContent {
                val density = LocalDensity.current
                val fontSizeInt by viewModel.fontSize.collectAsState()
                
                // Calculate dynamic heights based on text size.
                // Collapsed Height = 1 Line of text (approx 1.5x font size) + Vertical Padding (24dp) + Nav Bar.
                // This ensures the "Peek" strip is exactly tall enough to show one line of logs.
                val fontSizeSp = fontSizeInt.sp
                val fontSizePx = with(density) { fontSizeSp.toPx() }
                val paddingPx = with(density) { 24.dp.toPx() }
                
                val contentHeightPx = (fontSizePx * 1.5f) + paddingPx
                val collapsedTotalHeightPx = (contentHeightPx + navBarHeightPx).toInt()
                val collapsedHeightDp = with(density) { collapsedTotalHeightPx.toDp() }
                val navBarHeightDp = with(density) { navBarHeightPx.toDp() }

                // Determine initial state of the sheet.
                val sheetState = rememberBottomSheetState(
                    initialValue = BottomSheetValue.Collapsed
                )

                // Expose sheet state to the outer Service class for programmatic collapse.
                DisposableEffect(sheetState) {
                    bottomSheetState = sheetState
                    onDispose { bottomSheetState = null }
                }

                // Window State Synchronization Logic
                // This observes the Compose Sheet State and updates the WindowManager flags accordingly.
                LaunchedEffect(sheetState.value, isInteractingRaw) {
                    // Do not shrink the window while the user is actively touching it.
                    if (isInteractingRaw) return@LaunchedEffect

                    if (sheetState.value == BottomSheetValue.Collapsed) {
                        // Delay slightly to allow the collapse animation to visually finish before clipping the window.
                        delay(300)
                        if (!isInteractingRaw) {
                            setWindowToCollapsed(collapsedTotalHeightPx)
                        }
                    } else {
                        // If Expanded or Peeking (custom logic often maps peek to expanded in window terms),
                        // ensure the window fills the screen to receive touches everywhere.
                        setWindowToFullScreen(false)
                    }
                }

                LogKittyTheme {
                    LogBottomSheet(
                        sheetState = sheetState,
                        viewModel = viewModel,
                        screenHeight = with(density) { resources.displayMetrics.heightPixels.toDp() },
                        navBarHeight = navBarHeightDp,
                        collapsedHeightDp = collapsedHeightDp,
                        onSaveClick = {
                            // Launch the file saver activity (requires UI context).
                            val intent = Intent(this@LogKittyOverlayService, com.hereliesaz.logkitty.FileSaverActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        },
                        onSettingsClick = {
                            // Launch the settings activity.
                            val intent = Intent(this@LogKittyOverlayService, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.putExtra("EXTRA_SHOW_SETTINGS", true)
                            startActivity(intent)
                        }
                    )
                }
            }
        }

        // Initialize the LifecycleHelper to enable Jetpack Compose within this Service.
        lifecycleHelper = ComposeLifecycleHelper(composeView!!)
        lifecycleHelper!!.onCreate()
        lifecycleHelper!!.onStart()

        // Define initial window parameters (Collapsed state).
        val initialHeight = (100 * resources.displayMetrics.density).toInt()
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Required for drawing over other apps (API 26+)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or // Don't steal key input (allows typing in other apps)
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow drawing behind system bars
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // Use absolute coordinates
            PixelFormat.TRANSLUCENT // Transparent background
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0 // Anchor to the absolute bottom
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Expands the WindowManager layout to cover the entire screen.
     * @param forceTouchable If true, ensures the window accepts touches immediately (removes FLAG_NOT_TOUCHABLE).
     */
    private fun setWindowToFullScreen(forceTouchable: Boolean) {
        val params = composeView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            // Remove FLAG_NOT_TOUCHABLE so we can interact with the full UI.
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Shrinks the WindowManager layout to just the height of the collapsed "peek" strip.
     * This is crucial: it allows clicks on the rest of the screen to pass through to the underlying app.
     * @param heightPx The target height in pixels.
     */
    private fun setWindowToCollapsed(heightPx: Int) {
        val params = composeView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.height != heightPx) {
            params.height = heightPx
            // Remove FLAG_NOT_TOUCHABLE (logic seems redundant here, but ensures we are in a known state).
            // Note: Even with this flag removed, because the window SIZE is small, touches outside it pass through.
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try { windowManager.updateViewLayout(composeView, params) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Creates the Notification Channel required for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW // Low importance to avoid sound/vibration
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the persistent notification for the Foreground Service.
     */
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

        // Standard icon (ensure it exists in drawable resources)
        val icon = android.R.drawable.ic_menu_view

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LogKitty Running")
            .setContentText("Tap to Stop. Expand for Settings.")
            .setSmallIcon(icon)
            .setContentIntent(stopPendingIntent) // Tapping notification stops service (maybe change to open settings?)
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
