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
import androidx.compose.ui.draw.alpha
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

class MainActivity : ComponentActivity() {

    private var isOverlayGranted by mutableStateOf(false)
    private var isReadLogsGranted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        checkServiceStatus()
        
        requestRootAccess()

        if (intent?.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }

        setContent {
            LogKittyTheme {
                val lifecycle = LocalLifecycleOwner.current.lifecycle
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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            viewModel = (application as MainApplication).mainViewModel
                        )
                    } else {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }
    }

    private fun checkPermissions() {
        isOverlayGranted = Settings.canDrawOverlays(this)
        isReadLogsGranted = ContextCompat.checkSelfPermission(
            this, 
            android.Manifest.permission.READ_LOGS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
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

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun toggleOverlayService() {
        val intent = Intent(this, LogKittyOverlayService::class.java)
        if (isServiceRunning) {
            intent.action = "com.hereliesaz.logkitty.STOP_SERVICE"
            startService(intent)
            isServiceRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startForegroundService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                startService(intent)
            }
            isServiceRunning = true
            finish()
        }
    }

    private fun requestRootAccess() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su -c exit")
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    runOnUiThread {
                         (application as MainApplication).mainViewModel.setRootEnabled(true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}

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

    val canStart = isOverlayGranted && (isReadLogsGranted || isRootEnabled)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.logkitty),
                contentDescription = "LogKitty Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "LogKitty",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!isOverlayGranted) {
                PermissionCard(
                    title = "Overlay Permission Required",
                    description = "LogKitty needs to draw over other apps to function.",
                    buttonText = "Grant Overlay",
                    onClick = onGrantOverlay
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!isReadLogsGranted) {
                val cardAlpha = if (isRootEnabled) 0.5f else 1.0f
                val cardColor = if (isRootEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isRootEnabled) 0.dp else 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .alpha(cardAlpha),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isRootEnabled) "Standard Permission (Bypassed)" else "Standard Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRootEnabled) 
                                "Root access is enabled, so manual ADB permission is not required." 
                            else 
                                "Android requires a special permission to read logs. You must grant this via ADB:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        if (!isRootEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.READ_LOGS"
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = command,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            AzButton(
                                onClick = { clipboardManager.setText(AnnotatedString(command)) },
                                text = "Copy Command",
                                shape = AzButtonShape.RECTANGLE,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (canStart) {
                Text(
                    text = "Ready to Purr",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                AzButton(
                    onClick = onToggleService,
                    text = if (isServiceRunning) "Stop Service" else "Start Service",
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = AzButtonShape.RECTANGLE,
                    colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                )
            } else if (isRootEnabled) {
                if (isOverlayGranted) {
                     // Should be covered by canStart
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AzButton(
                onClick = onOpenSettings,
                text = "Settings",
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = AzButtonShape.RECTANGLE
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            AzButton(
                onClick = onClick,
                text = buttonText,
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
