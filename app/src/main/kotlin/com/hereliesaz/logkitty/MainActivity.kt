package com.hereliesaz.logkitty

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.services.LogKittyOverlayService
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
    private var isServiceRunning by mutableStateOf(false)

    // UI State for Navigation
    private var showSettings by mutableStateOf(false)

    // Activity Result Launcher for the "Display Over Other Apps" system settings screen.
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check permissions when returning from the system settings.
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial Checks
        checkPermissions()
        checkServiceStatus()

        // Attempt to gain root access silently if available (for "Root Mode").
        requestRootAccess()

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
                    if (showSettings) {
                        // Show the Settings Screen (Full Page).
                        SettingsScreen(
                            onBack = { showSettings = false },
                            viewModel = (application as MainApplication).mainViewModel
                        )
                    } else {
                        // Show the Main Dashboard / Permission Wizard.
                        val viewModel = (application as MainApplication).mainViewModel
                        val isRootEnabled by viewModel.isRootEnabled.collectAsState()

                        MainScreenContent(
                            isOverlayGranted = isOverlayGranted,
                            isReadLogsGranted = isReadLogsGranted,
                            isRootEnabled = isRootEnabled,
                            isServiceRunning = isServiceRunning,
                            onGrantOverlay = { requestOverlayPermission() },
                            onToggleService = { toggleOverlayService() },
                            onOpenSettings = { showSettings = true }
                        )
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
    }
    
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
        val intent = Intent(this, LogKittyOverlayService::class.java)
        if (isServiceRunning) {
            intent.action = "com.hereliesaz.logkitty.STOP_SERVICE"
            startService(intent)
            isServiceRunning = false
        } else {
            // Start as Foreground Service.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { startForegroundService(intent) } catch (e: Exception) { android.util.Log.e("MainActivity", "Failed to start foreground service", e) }
            } else {
                startService(intent)
            }
            isServiceRunning = true
            // Close the activity so the user sees the overlay immediately.
            finish()
        }
    }

    /**
     * Checks if Root access is available by executing `su -c exit`.
     * If successful, updates the ViewModel to enable Root features.
     */
    private fun requestRootAccess() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su -c exit")
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    runOnUiThread { (application as MainApplication).mainViewModel.setRootEnabled(true) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
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
    isServiceRunning: Boolean,
    onGrantOverlay: () -> Unit,
    onToggleService: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Condition to allow starting the service: Overlay MUST be granted.
    // Read Logs OR Root is required for functionality, but we might allow start even if missing (to show empty logs).
    // Here we strictly require at least one method of reading logs.
    val canStart = isOverlayGranted && (isReadLogsGranted || isRootEnabled)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.logkitty),
                contentDescription = "LogKitty Logo",
                modifier = Modifier.size(120.dp).padding(bottom = 16.dp)
            )
            Text(text = "LogKitty", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(32.dp))

            // Step 1: Overlay Permission
            if (!isOverlayGranted) {
                PermissionCard(
                    title = "Overlay Permission Required",
                    description = "LogKitty needs to draw over other apps.",
                    buttonText = "Grant Overlay",
                    onClick = onGrantOverlay
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 2: Read Logs Permission (Only if Root is not active)
            if (!isReadLogsGranted && !isRootEnabled) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Standard Permission Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Run this ADB command:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS"
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)), modifier = Modifier.fillMaxWidth()) {
                            Text(command, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AzButton(onClick = { clipboardManager.setText(AnnotatedString(command)) }, text = "Copy Command", shape = AzButtonShape.RECTANGLE, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Step 3: Start Button
            if (canStart) {
                Text("Ready to Purr", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(16.dp))
                AzButton(
                    onClick = onToggleService,
                    text = if (isServiceRunning) "Stop Service" else "Start Service",
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = AzButtonShape.RECTANGLE,
                    colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AzButton(onClick = onOpenSettings, text = "Settings", modifier = Modifier.fillMaxWidth().height(56.dp), shape = AzButtonShape.RECTANGLE)
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
