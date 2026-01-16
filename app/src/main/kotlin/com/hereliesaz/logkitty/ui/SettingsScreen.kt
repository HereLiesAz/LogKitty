package com.hereliesaz.logkitty.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.logkitty.services.IdeazOverlayService
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(false) }
    var readLogsGranted by remember { mutableStateOf(false) }

    // Check permissions on Resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
                readLogsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            PermissionItem(
                title = "Overlay Permission",
                description = "Required to display logs over other apps.",
                isGranted = overlayGranted,
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
            )

            PermissionItem(
                title = "Read Logs Permission",
                description = "Required to read system logs. Grant via ADB:\nadb shell pm grant ${context.packageName} android.permission.READ_LOGS",
                isGranted = readLogsGranted,
                onClick = {
                     val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant ${context.packageName} android.permission.READ_LOGS")
                     clipboard.setPrimaryClip(clip)
                     Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            Divider()

            Text("System", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open System App Info")
            }

            Button(
                onClick = {
                    context.stopService(Intent(context, IdeazOverlayService::class.java))
                    // We can't easily kill the accessibility service, but we can kill the process
                    exitProcess(0)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Exit LogKitty")
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = isGranted,
            onCheckedChange = { onClick() },
            // Read Logs is not togglable via UI usually (it's hard permission or adb), but switch indicates state
            enabled = true
        )
    }
}
