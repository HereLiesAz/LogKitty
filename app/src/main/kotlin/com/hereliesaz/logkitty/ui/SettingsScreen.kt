package com.hereliesaz.logkitty.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.logkitty.services.LogKittyOverlayService
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for managing sub-screens within settings
    var showProhibitedLogs by remember { mutableStateOf(false) }

    if (showProhibitedLogs && viewModel != null) {
        ProhibitedLogsScreen(
            viewModel = viewModel,
            onBack = { showProhibitedLogs = false }
        )
        return
    }

    var overlayGranted by remember { mutableStateOf(false) }
    var readLogsGranted by remember { mutableStateOf(false) }

    val overlayOpacity = viewModel?.overlayOpacity?.collectAsState()
    val customFilter = viewModel?.customFilter?.collectAsState()
    val isRootEnabled = viewModel?.isRootEnabled?.collectAsState()
    val logColors = viewModel?.logColors?.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Color Picker State
    var showColorPicker by remember { mutableStateOf<LogLevel?>(null) }

    if (showColorPicker != null && viewModel != null && logColors != null) {
        val level = showColorPicker!!
        val currentColor = logColors.value[level] ?: level.defaultColor
        ColorPickerDialog(
            initialColor = currentColor,
            onColorSelected = { newColor ->
                viewModel.setLogColor(level, newColor)
                showColorPicker = null
            },
            onDismissRequest = { showColorPicker = null }
        )
    }


    // Export/Import Launchers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && viewModel != null) {
            scope.launch {
                try {
                    val json = viewModel.exportPreferences()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    }
                    snackbarHostState.showSnackbar("Preferences exported successfully.")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && viewModel != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                    if (json != null) {
                        viewModel.importPreferences(json)
                        snackbarHostState.showSnackbar("Preferences imported successfully.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Import failed: ${e.localizedMessage}")
                }
            }
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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

            if (viewModel != null) {
                PermissionItem(
                    title = "Root Access",
                    description = "Use root access to read logs (Alternative to ADB permission).",
                    isGranted = isRootEnabled?.value ?: false,
                    onClick = {
                        val currentValue = isRootEnabled?.value ?: false
                        if (!currentValue) {
                            // Try to enable
                            scope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    try {
                                        Runtime.getRuntime().exec("su -c ls").waitFor() == 0
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                if (success) {
                                    viewModel.setRootEnabled(true)
                                } else {
                                    snackbarHostState.showSnackbar("Root access denied or not available.")
                                }
                            }
                        } else {
                            // Disable
                            viewModel.setRootEnabled(false)
                        }
                    }
                )
            }

            HorizontalDivider()

            if (viewModel != null) {
                Text("Configuration", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay Transparency", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = overlayOpacity?.value ?: 1f,
                        onValueChange = { viewModel.setOverlayOpacity(it) },
                        valueRange = 0.1f..1f
                    )
                }

                OutlinedTextField(
                    value = customFilter?.value ?: "",
                    onValueChange = { viewModel.setCustomFilter(it) },
                    label = { Text("Global Log Filter") },
                    placeholder = { Text("Filter logs...") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Customization
                Text("Log Colors", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logColors?.value?.forEach { (level, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showColorPicker = level }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(level.name, style = MaterialTheme.typography.bodyMedium)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.resetLogColors() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Reset Colors to Default")
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = { showProhibitedLogs = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Prohibited Logs")
                }

                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { createDocumentLauncher.launch("logkitty_prefs.json") }
                    ) {
                        Text("Export Settings")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { openDocumentLauncher.launch(arrayOf("application/json")) }
                    ) {
                        Text("Import Settings")
                    }
                }

                HorizontalDivider()
            }

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
                    context.stopService(Intent(context, LogKittyOverlayService::class.java))
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
