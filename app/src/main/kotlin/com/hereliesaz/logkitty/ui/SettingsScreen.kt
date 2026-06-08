package com.hereliesaz.logkitty.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hereliesaz.logkitty.BuildConfig
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.ui.theme.CodingFont
import com.hereliesaz.logkitty.utils.LogSources

/**
 * [SettingsScreen] provides a full-screen configuration UI with three navigation targets:
 * the main settings list, the prohibited-tags manager, and the color-scheme customizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: MainViewModel) {
    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }

    when (currentRoute) {
        SettingsRoute.MAIN -> SettingsMainScreen(
            viewModel = viewModel,
            onBack = onBack,
            onOpenProhibited = { currentRoute = SettingsRoute.PROHIBITED },
            onOpenColorEditor = { currentRoute = SettingsRoute.COLORS }
        )
        SettingsRoute.PROHIBITED -> ProhibitedLogsScreen(
            viewModel = viewModel,
            onBack = { currentRoute = SettingsRoute.MAIN }
        )
        SettingsRoute.COLORS -> ColorSchemeEditorScreen(
            viewModel = viewModel,
            onBack = { currentRoute = SettingsRoute.MAIN }
        )
    }
}

private enum class SettingsRoute { MAIN, PROHIBITED, COLORS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenProhibited: () -> Unit,
    onOpenColorEditor: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val overlayOpacity by viewModel.overlayOpacity.collectAsState()
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val isContextMode by viewModel.isContextModeEnabled.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()
    val isLogReversed by viewModel.isLogReversed.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamily.collectAsState()
    val showTimestamp by viewModel.showTimestamp.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val activeLevels by viewModel.activeLogLevels.collectAsState()
    val colorScheme by viewModel.colorScheme.collectAsState()
    val tagColoringEnabled by viewModel.tagColoringEnabled.collectAsState()
    val prohibitedCount by viewModel.prohibitedTags.collectAsState()
    val monitoredApps by viewModel.monitoredApps.collectAsState()
    val activeSourceFilters by viewModel.activeSourceFilters.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showSchemeMenu by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(backgroundColorInt),
            onDismiss = { showColorPicker = false },
            onColorSelected = {
                viewModel.setBackgroundColor(it.toArgb())
                showColorPicker = false
            }
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = {
                viewModel.addMonitoredApp(it)
                showAppPicker = false
            }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(viewModel.exportPreferences().toByteArray())
            }
            Toast.makeText(context, "Preferences exported", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val ok = viewModel.importPreferences(text)
            Toast.makeText(context, if (ok) "Preferences imported" else "Invalid file", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            SettingsSectionHeader("Appearance")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showColorPicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Background Color", style = MaterialTheme.typography.bodyLarge)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(backgroundColorInt))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
            HorizontalDivider()

            Text(
                "Background Opacity: ${(overlayOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
            Slider(
                value = overlayOpacity,
                onValueChange = { viewModel.setOverlayOpacity(it) },
                valueRange = 0.1f..1.0f,
                steps = 9
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Log Colors")
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Color Scheme", style = MaterialTheme.typography.bodyLarge)
                Box {
                    OutlinedButton(onClick = { showSchemeMenu = true }) { Text(colorScheme.displayName) }
                    DropdownMenu(expanded = showSchemeMenu, onDismissRequest = { showSchemeMenu = false }) {
                        LogColorScheme.values().forEach { scheme ->
                            DropdownMenuItem(
                                text = { Text(scheme.displayName) },
                                onClick = {
                                    viewModel.setColorScheme(scheme)
                                    showSchemeMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tag-Based Coloring", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = tagColoringEnabled, onCheckedChange = { viewModel.setTagColoringEnabled(it) })
            }
            AzButton(
                onClick = onOpenColorEditor,
                text = "Customize Per-Level Colors",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Typography")
            Text("Font Size: ${fontSize}sp", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { viewModel.setFontSize(it.toInt()) },
                valueRange = 8f..24f,
                steps = 15
            )

            var fontExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { fontExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape
                ) {
                    Text("Font: $fontFamilyName")
                }
                DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                    CodingFont.values().forEach { font ->
                        DropdownMenuItem(
                            text = { Text(font.displayName) },
                            onClick = {
                                viewModel.setFontFamily(font)
                                fontExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Timestamps", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = showTimestamp, onCheckedChange = { viewModel.setShowTimestamp(it) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Behavior")

            Text("Active Log Levels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LogLevel.values().forEach { level ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Checkbox(
                            checked = activeLevels.contains(level.name),
                            onCheckedChange = { viewModel.toggleLogLevel(level, it) }
                        )
                        Text(level.name.first().toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            var bufferExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Buffer Size", style = MaterialTheme.typography.bodyLarge)
                Box {
                    OutlinedButton(onClick = { bufferExpanded = true }) { Text(bufferSize.toString()) }
                    DropdownMenu(expanded = bufferExpanded, onDismissRequest = { bufferExpanded = false }) {
                        listOf(1000, 2000, 5000, 10000).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size lines") },
                                onClick = {
                                    viewModel.setBufferSize(size)
                                    bufferExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Context Mode (Auto-Filter)", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isContextMode, onCheckedChange = { viewModel.toggleContextMode() })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Root Access", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isRootEnabled, onCheckedChange = { viewModel.setRootEnabled(it) })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reverse Log Order", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isLogReversed, onCheckedChange = { viewModel.setLogReversed(it) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("App Monitoring")
            Text(
                "Pin an app to get a dedicated tab showing only its logs, in addition to the full system log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (monitoredApps.isEmpty()) {
                Text(
                    "No apps pinned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                monitoredApps.forEach { pkg ->
                    // Resolve the app label off the main thread to avoid blocking PackageManager IPC.
                    val label by produceState(initialValue = pkg, pkg) {
                        value = withContext(Dispatchers.IO) { appLabel(context, pkg) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeMonitoredApp(pkg) }) {
                            Icon(Icons.Default.Close, contentDescription = "Stop monitoring $pkg")
                        }
                    }
                }
            }
            AzButton(
                onClick = { showAppPicker = true },
                text = "Add App to Monitor",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Log sources")
            Text(
                "Add tabs that show only logs from a chosen source. The All tab always shows " +
                    "everything. (Source/category classification needs the UID column — root or " +
                    "ADB READ_LOGS; lines without a UID appear only in All.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SourceFilterGroup(
                title = "Sources",
                keys = LogSources.SOURCE_BUCKETS,
                enabled = activeSourceFilters,
                onToggle = { key, on -> viewModel.setSourceFilterEnabled(key, on) }
            )
            SourceFilterGroup(
                title = "Categories",
                keys = LogSources.CATEGORY_BUCKETS,
                enabled = activeSourceFilters,
                onToggle = { key, on -> viewModel.setSourceFilterEnabled(key, on) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Filters")
            AzButton(
                onClick = onOpenProhibited,
                text = "Prohibited Tags (${prohibitedCount.size})",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingsSectionHeader("Backup")
            AzButton(
                onClick = { exportLauncher.launch("logkitty_prefs.json") },
                text = "Export Preferences",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            AzButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                text = "Import Preferences",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            AzButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(viewModel.exportPreferences()))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                text = "Copy Preferences JSON",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            AzButton(
                onClick = { viewModel.clearLog() },
                text = "Clear Log",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            AzButton(
                onClick = { viewModel.resetLogColors() },
                text = "Reset Colors",
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SettingsFooter(context)

            Spacer(modifier = Modifier.height(16.dp))
            SettingsAdBanner()

            // Large trailing margin so the footer can be scrolled well up the screen.
            Spacer(modifier = Modifier.height(240.dp))
        }
    }
}

/** Resolves a user-facing app label for a package, falling back to the package name. */
private fun appLabel(context: android.content.Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) { pkg }

/**
 * Bottom-of-settings footer mirroring AzNavRail's footer: About (repo), Feedback (email),
 * and the author's handle (Instagram).
 */
@Composable
private fun SettingsFooter(context: android.content.Context) {
    fun open(intent: android.content.Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "No app found to handle this", Toast.LENGTH_SHORT).show() }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // padding before clickable so the whole padded area is the touch target / ripple bounds.
            Text(
                "About",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/HereLiesAz/LogKitty")))
                    }
            )
            Text(
                "Feedback",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_SENDTO,
                            Uri.parse("mailto:hereliesaz@gmail.com?subject=LogKitty")))
                    }
            )
            Text(
                "@HereLiesAz",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://instagram.com/HereLiesAz")))
                    }
            )
        }
        Text(
            "LogKitty v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * AdMob banner shown at the bottom of Settings. Uses Google's official TEST ad unit for now.
 * TODO: replace [TEST_BANNER_AD_UNIT] with the real ad-unit ID and add a UMP consent flow
 * (the app ID lives in AndroidManifest; SDK init is in MainApplication).
 */
@Composable
private fun SettingsAdBanner() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val adView = remember {
        com.google.android.gms.ads.AdView(context).apply {
            setAdSize(com.google.android.gms.ads.AdSize.BANNER)
            adUnitId = TEST_BANNER_AD_UNIT
            loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        }
    }

    // Pause/resume/destroy with the host lifecycle (AdMob policy + battery/memory).
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> adView.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> adView.pause()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    // Fixed banner height avoids a layout shift (and accidental taps) when the ad loads.
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        factory = { adView }
    )
}

private const val TEST_BANNER_AD_UNIT = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A labelled sub-group of source-filter checkboxes (e.g. "Sources" or "Categories"). */
@Composable
private fun SourceFilterGroup(
    title: String,
    keys: List<String>,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
    keys.forEach { key ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(key, key !in enabled) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // onCheckedChange = null: the Row's clickable owns the toggle, merging both into one
            // accessible touch target.
            Checkbox(
                checked = key in enabled,
                onCheckedChange = null
            )
            Text(LogSources.label(key), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
