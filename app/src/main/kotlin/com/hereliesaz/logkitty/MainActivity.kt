package com.hereliesaz.logkitty

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.logkitty.core.shell.RootShell
import kotlinx.coroutines.launch
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.services.LogKittyOverlayService
import com.hereliesaz.logkitty.ui.GitHubScreen
import com.hereliesaz.logkitty.ui.AnalyzerDashboardScreen
import com.hereliesaz.logkitty.ui.SettingsScreen
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme

/**
 * [MainActivity] is the user-facing entry point for configuration.
 *
 * It is NOT the main overlay UI. Instead, it serves as a "Wizard" or "Dashboard" to:
 * 1. Check and request necessary permissions (Overlay, Read Logs).
 * 2. Toggle the [LogKittyOverlayService] on and off.
 * 3. Provide access to the App Settings.
 *
 * It manages the [SettingsScreen] flow and provides instructions for ADB permissions.
 */
class MainActivity : ComponentActivity() {

    // UI State for Permission Status
    private var isOverlayGranted by mutableStateOf(false)
    private var isReadLogsGranted by mutableStateOf(false)
    private var isUsageGranted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)

    // BroadcastReceiver to monitor Service Death
    private val serviceStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isServiceRunning = false
        }
    }

    // UI State for Navigation
    private var showSettings by mutableStateOf(false)
    private var showGitHub by mutableStateOf(false)

    // Activity Result Launcher for the "Display Over Other Apps" system settings screen.
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permissions when returning from the system settings.
        checkPermissions()
    }

    // Runtime request for POST_NOTIFICATIONS (Android 13+). Without it the foreground-service
    // notification — LogKitty's persistent silent control surface — is suppressed by the OS.
    // Either way (granted or denied) we proceed to start the service; the notification simply
    // appears once the permission is held.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startOverlayService() }

    // --- Play in-app updates (flexible) ---
    // Only does anything for Play-installed builds; on sideloaded/GitHub builds no update is found.
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private var updateReadyToInstall by mutableStateOf(false)
    private val installStateListener = InstallStateUpdatedListener { state ->
        // Bind the prompt to the download status so it clears if the update is canceled or fails,
        // rather than latching on once and showing a stale "Restart" card.
        updateReadyToInstall = state.installStatus() == InstallStatus.DOWNLOADED
    }
    // Flexible-update download runs in the background; progress is tracked via installStateListener,
    // so the result itself isn't needed here.
    private val appUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars. From Android 15 (targetSdk 35+) the platform forces this on
        // regardless, and the `windowOptOutEdgeToEdgeEnforcement` escape hatch is ignored from
        // targetSdk 36 — so the only question is whether we opt in deliberately or get it done to
        // us. Calling it here also back-ports the same layout to API 30-34, so there is one inset
        // behaviour to reason about instead of two. Must run before setContent so the first frame
        // is already edge-to-edge. Every screen below is responsible for its own safe-area padding:
        // the Scaffold-based screens get it from Scaffold/TopAppBar defaults, and MainScreenContent
        // applies WindowInsets.safeDrawing itself.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register receiver for service death
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStoppedReceiver, IntentFilter("com.hereliesaz.logkitty.ACTION_SERVICE_STOPPED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStoppedReceiver, IntentFilter("com.hereliesaz.logkitty.ACTION_SERVICE_STOPPED"))
        }

        // Initial Checks
        checkPermissions()
        checkServiceStatus()

        // Attempt to gain root access silently if available (for "Root Mode").
        requestRootAccess()

        // Offer a flexible in-app update if Play has a newer build.
        checkForAppUpdate()

        // Handle intent arguments (e.g., opening directly to Settings from the Notification).
        if (intent?.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }

        setContent {
            LogKittyTheme {
                val lifecycle = LocalLifecycleOwner.current.lifecycle

                // Observe lifecycle to re-check status when user returns to the app (e.g., after granting permissions).
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            checkServiceStatus()
                            checkPermissions()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel = (application as MainApplication).mainViewModel
                    // One persistent banner anchored to the bottom of every in-app screen. Composed
                    // once here, outside the navigation `when`, so the same AdView instance survives
                    // dashboard <-> Settings <-> GitHub navigation instead of reloading per screen.
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            when {
                                showSettings ->
                                    // Show the Settings Screen (Full Page).
                                    SettingsScreen(
                                        onBack = { showSettings = false },
                                        viewModel = viewModel
                                    )
                                showGitHub ->
                                    // Full-screen GitHub Actions (same panel the overlay tab hosts).
                                    GitHubScreen(
                                        viewModel = viewModel,
                                        onBack = { showGitHub = false },
                                        // Keep showGitHub=true: the when checks showSettings first, so Settings
                                        // shows on top and backing out of it returns here, not the dashboard.
                                        onConfigure = { showSettings = true },
                                    )
                                else -> {
                                    // Show the Main Dashboard / Permission Wizard.
                                    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
                                    val canStart = isOverlayGranted && (isReadLogsGranted || isRootEnabled)

                                    if (!canStart || updateReadyToInstall) {
                                        MainScreenContent(
                                            isOverlayGranted = isOverlayGranted,
                                            isReadLogsGranted = isReadLogsGranted,
                                            isRootEnabled = isRootEnabled,
                                            isUsageGranted = isUsageGranted,
                                            isServiceRunning = isServiceRunning,
                                            updateReadyToInstall = updateReadyToInstall,
                                            onCompleteUpdate = { appUpdateManager.completeUpdate() },
                                            onGrantOverlay = { requestOverlayPermission() },
                                            onToggleService = { toggleOverlayService() },
                                            onOpenSettings = { showSettings = true },
                                            onOpenGitHub = { showGitHub = true }
                                        )
                                    } else {
                                        AnalyzerDashboardScreen(
                                            isServiceRunning = isServiceRunning,
                                            onToggleService = { toggleOverlayService() },
                                            onOpenSettings = { showSettings = true },
                                            onOpenGitHub = { showGitHub = true },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles new intents (e.g. tapping the notification while the activity is already alive).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }
    }

    /**
     * verifies if the app has the necessary system permissions.
     */
    private fun checkPermissions() {
        isOverlayGranted = Settings.canDrawOverlays(this)
        // Check for READ_LOGS. Note: This is a "Signature|Privileged|Development" permission.
        // Normal apps cannot get it via a prompt; it must be granted via ADB.
        isReadLogsGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
        
        isUsageGranted = hasUsageStatsAccess(this)
    }

    private fun hasUsageStatsAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }
    
    /**
     * Checks if the [LogKittyOverlayService] is currently running.
     * Uses ActivityManager (Legacy method, but reliable for checking own services).
     */
    @Suppress("DEPRECATION")
    private fun checkServiceStatus() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isServiceRunning = false
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (LogKittyOverlayService::class.java.name == service.service.className) {
                isServiceRunning = true
                break
            }
        }
    }

    /**
     * Directs the user to the system settings page to grant overlay permission.
     */
    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    /**
     * Starts or Stops the Overlay Service based on current state.
     */
    private fun toggleOverlayService() {
        if (isServiceRunning) {
            val intent = Intent(this, LogKittyOverlayService::class.java)
            intent.action = "com.hereliesaz.logkitty.STOP_SERVICE"
            startService(intent)
            isServiceRunning = false
            return
        }
        // Starting: on Android 13+ make sure we can post the foreground-service notification first.
        // The launcher callback calls startOverlayService() once the user answers; if we already
        // have the permission (or are pre-33) we start immediately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startOverlayService()
        }
    }

    /** Starts the overlay foreground service and closes the dashboard so the overlay is visible. */
    private fun startOverlayService() {
        val intent = Intent(this, LogKittyOverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // Only mark running and close the dashboard once the start actually succeeded.
            isServiceRunning = true
            finish()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start foreground service", e)
        }
    }

    /**
     * Checks if Root access is available (via [RootShell.isRootAvailable], a bounded `su -c id -u`
     * probe with a watchdog) and, if so, enables Root features. Runs on [lifecycleScope] so the work
     * is cancelled with the Activity and a hung `su` can't leak a thread for the process lifetime.
     */
    private fun requestRootAccess() {
        lifecycleScope.launch {
            if (RootShell.isRootAvailable()) {
                (application as MainApplication).mainViewModel.setRootEnabled(true)
            }
        }
    }

    /** Starts a flexible Play update if one is available, or surfaces the restart prompt if already downloaded. */
    private fun checkForAppUpdate() {
        appUpdateManager.registerListener(installStateListener)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            updateReadyToInstall = info.installStatus() == InstallStatus.DOWNLOADED
            if (!updateReadyToInstall &&
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info, appUpdateLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-sync the restart prompt with the actual download state so a stale prompt isn't shown.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            updateReadyToInstall = info.installStatus() == InstallStatus.DOWNLOADED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateListener)
        unregisterReceiver(serviceStoppedReceiver)
    }
}

/**
 * The primary dashboard UI composable.
 */
@Composable
fun MainScreenContent(
    isOverlayGranted: Boolean,
    isReadLogsGranted: Boolean,
    isRootEnabled: Boolean,
    isUsageGranted: Boolean,
    isServiceRunning: Boolean,
    updateReadyToInstall: Boolean,
    onCompleteUpdate: () -> Unit,
    onGrantOverlay: () -> Unit,
    onToggleService: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Condition to allow starting the service: Overlay MUST be granted.
    // Read Logs OR Root is required for functionality.
    val canStart = isOverlayGranted && (isReadLogsGranted || isRootEnabled)

    // Unlike the other screens this one has no Scaffold, so nothing applies the safe area for it.
    // Under edge-to-edge the logo would slide under the status bar and the bottom buttons under the
    // navigation bar / gesture handle, so inset the scroll viewport here. safeDrawing (rather than
    // systemBars) also covers display cutouts and the IME.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.logkitty),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier = Modifier.size(120.dp).padding(bottom = 16.dp)
            )
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(32.dp))

            // In-app update ready (flexible update finished downloading) → offer restart.
            if (updateReadyToInstall) {
                PermissionCard(
                    title = stringResource(R.string.update_ready_title),
                    description = stringResource(R.string.update_ready_desc),
                    buttonText = stringResource(R.string.update_restart),
                    onClick = onCompleteUpdate
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 1: Overlay Permission
            if (!isOverlayGranted) {
                PermissionCard(
                    title = stringResource(R.string.main_overlay_permission_title),
                    description = stringResource(R.string.main_overlay_permission_desc),
                    buttonText = stringResource(R.string.main_grant_overlay),
                    onClick = onGrantOverlay
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 2: Read Logs Permission (Only if Root is not active)
            if (!isReadLogsGranted && !isRootEnabled) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.main_standard_permission_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.main_run_adb_command), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS"
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)), modifier = Modifier.fillMaxWidth()) {
                            Text(command, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AzButton(onClick = { clipboardManager.setText(AnnotatedString(command)) }, text = stringResource(R.string.main_copy_command), shape = AzButtonShape.RECTANGLE, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 3: Usage Access
            if (!isUsageGranted && !isRootEnabled) {
                PermissionCard(
                    title = "Usage Access",
                    description = "Required for Context Mode to automatically detect the foreground app.",
                    buttonText = "Grant Usage Access",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 4: Start Button
            if (canStart) {
                Text(stringResource(R.string.main_ready_to_purr), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(16.dp))
                AzButton(
                    onClick = onToggleService,
                    text = if (isServiceRunning) stringResource(R.string.main_stop_service) else stringResource(R.string.main_start_service),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = AzButtonShape.RECTANGLE,
                    colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AzButton(onClick = onOpenSettings, text = stringResource(R.string.settings), modifier = Modifier.fillMaxWidth().height(56.dp), shape = AzButtonShape.RECTANGLE)
            Spacer(modifier = Modifier.height(8.dp))
            AzButton(onClick = onOpenGitHub, text = stringResource(R.string.main_open_github), modifier = Modifier.fillMaxWidth().height(56.dp), shape = AzButtonShape.RECTANGLE)
        }
    }
}

@Composable
fun PermissionCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            AzButton(onClick = onClick, text = buttonText, shape = AzButtonShape.RECTANGLE, modifier = Modifier.fillMaxWidth())
        }
    }
}
