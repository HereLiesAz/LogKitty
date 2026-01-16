package com.hereliesaz.logkitty

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
import androidx.compose.ui.unit.dp
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.services.LogKittyOverlayService
import com.hereliesaz.logkitty.ui.SettingsScreen
import com.hereliesaz.logkitty.ui.theme.LogKittyTheme

class MainActivity : ComponentActivity() {

    private var isOverlayGranted by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()
        requestRootAccess()

        if (intent?.getBooleanExtra("EXTRA_SHOW_SETTINGS", false) == true) {
            showSettings = true
        }

        setContent {
            LogKittyTheme {
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
                            onGrantPermission = { requestOverlayPermission() },
                            onStartOverlay = { startOverlayService() },
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

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, LogKittyOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish() // Close activity after starting service
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
                // Root not available or denied
                e.printStackTrace()
            }
        }.start()
    }
}

@Composable
fun MainScreenContent(
    isOverlayGranted: Boolean,
    onGrantPermission: () -> Unit,
    onStartOverlay: () -> Unit,
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
            // Replaced emoji text with image
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
                    text = "To float above other apps, LogKitty needs permission to display over other apps.",
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
                    text = "Permission Granted!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(16.dp))
                AzButton(
                    onClick = onStartOverlay,
                    text = "Start Overlay",
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = AzButtonShape.RECTANGLE
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
