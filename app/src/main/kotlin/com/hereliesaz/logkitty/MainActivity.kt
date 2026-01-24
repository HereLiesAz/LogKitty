package com.hereliesaz.logkitty

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.services.LogKittyOverlayService
import com.hereliesaz.logkitty.ui.SettingsScreen
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme

class MainActivity : ComponentActivity() {

    private var isOverlayGranted by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()
        checkServiceStatus()
        requestRootAccess()

        if (intent?.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }

        setContent {
            LogKittyTheme {
                // Monitor lifecycle to refresh service status on resume
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            checkServiceStatus()
                            checkOverlayPermission()
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
                        MainScreenContent(
                            isOverlayGranted = isOverlayGranted,
                            isServiceRunning = isServiceRunning,
                            onGrantPermission = { requestOverlayPermission() },
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

    private fun checkOverlayPermission() {
        isOverlayGranted = Settings.canDrawOverlays(this)
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
            intent.action = "com.hereliesaz.logkitty.STOP_SERVICE" // Match the constant in Service
            startService(intent) // Sending stop command
            isServiceRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // We assume it starts successfully; the lifecycle observer will confirm on next resume
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
    isServiceRunning: Boolean,
    onGrantPermission: () -> Unit,
    onToggleService: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                Text(
                    text = "LogKitty needs permission to display over other apps.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                AzButton(
                    onClick = onGrantPermission,
                    text = "Grant Overlay Permission",
                    shape = AzButtonShape.RECTANGLE
                )
            } else {
                Text(
                    text = "Ready to Purr",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Toggle Button
                AzButton(
                    onClick = onToggleService,
                    text = if (isServiceRunning) "Stop Service" else "Start Service",
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = AzButtonShape.RECTANGLE,
                    colors = if (isServiceRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                )
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
