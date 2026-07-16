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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hereliesaz.aznavrail.AzButton
import com.hereliesaz.aznavrail.model.AzButtonShape
import com.hereliesaz.logkitty.BuildConfig
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.core.feature.AdsFeature
import com.hereliesaz.logkitty.core.feature.FeatureLoader
import com.hereliesaz.logkitty.core.feature.FeatureModules
import com.hereliesaz.logkitty.feature.FeatureInstallStatus
import com.hereliesaz.logkitty.feature.rememberFeatureInstall
import com.hereliesaz.logkitty.utils.GitHubDeviceAuth
import kotlinx.coroutines.Job
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
            onOpenColorEditor = { currentRoute = SettingsRoute.COLORS },
            onOpenInfo = { currentRoute = SettingsRoute.INFO }
        )
        SettingsRoute.PROHIBITED -> ProhibitedLogsScreen(
            viewModel = viewModel,
            onBack = { currentRoute = SettingsRoute.MAIN }
        )
        SettingsRoute.COLORS -> ColorSchemeEditorScreen(
            viewModel = viewModel,
            onBack = { currentRoute = SettingsRoute.MAIN }
        )
        SettingsRoute.INFO -> InfoScreen(
            onBack = { currentRoute = SettingsRoute.MAIN }
        )
    }
}

private enum class SettingsRoute { MAIN, PROHIBITED, COLORS, INFO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenProhibited: () -> Unit,
    onOpenColorEditor: () -> Unit,
    onOpenInfo: () -> Unit,
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
    val githubOwner by viewModel.githubOwner.collectAsState(initial = "")
    val githubRepo by viewModel.githubRepo.collectAsState(initial = "")
    val hasGithubToken by viewModel.hasGithubToken.collectAsState(initial = false)
    val autoDeleteDurationDays by viewModel.autoDeleteDurationDays.collectAsState()
    val maxTotalLogSizeMegabytes by viewModel.maxTotalLogSizeMegabytes.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showSchemeMenu by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showContextDisclosure by remember { mutableStateOf(false) }

    // Track whether Usage Access is granted (Context Mode's foreground detection needs it on
    // non-root devices), refreshed on resume so the warning/toggle stay accurate.
    var isUsageGranted by remember { mutableStateOf(hasUsageStatsAccess(context)) }
    val contextLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(contextLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isUsageGranted = hasUsageStatsAccess(context)
            }
        }
        contextLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { contextLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showContextDisclosure) {
        ContextModeDisclosureDialog(
            onDismiss = { showContextDisclosure = false },
            onAgree = {
                showContextDisclosure = false
                viewModel.toggleContextMode() // enable
                // Non-root foreground detection uses Usage Access — send the user to grant it.
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.toast_no_app_to_handle), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

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
            Toast.makeText(context, context.getString(R.string.toast_prefs_exported), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, context.getString(R.string.toast_export_failed, it.message), Toast.LENGTH_LONG).show() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val ok = viewModel.importPreferences(text)
            Toast.makeText(context, context.getString(if (ok) R.string.toast_prefs_imported else R.string.toast_invalid_file), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context, context.getString(R.string.toast_import_failed, it.message), Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
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
            SettingsCard {
                PermissionsSection(context)
            }

            var showAdFreeDialog by remember { mutableStateOf(false) }
            if (showAdFreeDialog) {
                com.hereliesaz.logkitty.ui.AdFreeDialog(
                    billingManager = viewModel.billingManager,
                    onDismiss = { showAdFreeDialog = false }
                )
            }

            if (!com.hereliesaz.logkitty.billing.AdsState.isAdFreePermanently.value) {
                SettingsCard("Premium") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdFreeDialog = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Go Ad-Free", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Unlock LogKitty permanently or enjoy a free session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SettingsCard(stringResource(R.string.settings_section_display)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showColorPicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_background_color), style = MaterialTheme.typography.bodyLarge)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(backgroundColorInt))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Text(
                    stringResource(R.string.settings_background_opacity, (overlayOpacity * 100).toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Slider(
                    value = overlayOpacity,
                    onValueChange = { viewModel.setOverlayOpacity(it) },
                    valueRange = 0.1f..1.0f,
                    steps = 9
                )

                Text(
                    stringResource(R.string.settings_font_size, fontSize),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
                        val currentFont = remember(fontFamilyName) {
                            try { CodingFont.valueOf(fontFamilyName) } catch (e: Exception) { CodingFont.SYSTEM }
                        }
                        Text(stringResource(R.string.settings_font, stringResource(currentFont.displayNameRes)))
                    }
                    DropdownMenu(expanded = fontExpanded, onDismissRequest = { fontExpanded = false }) {
                        CodingFont.values().forEach { font ->
                            DropdownMenuItem(
                                text = { Text(stringResource(font.displayNameRes)) },
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
                    Text(stringResource(R.string.settings_show_timestamps), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = showTimestamp, onCheckedChange = { viewModel.setShowTimestamp(it) })
                }
            }

            SettingsCard(stringResource(R.string.settings_section_log_colors)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_color_scheme), style = MaterialTheme.typography.bodyLarge)
                    Box {
                        OutlinedButton(onClick = { showSchemeMenu = true }) { Text(stringResource(colorScheme.displayNameRes)) }
                        DropdownMenu(expanded = showSchemeMenu, onDismissRequest = { showSchemeMenu = false }) {
                            LogColorScheme.values().forEach { scheme ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(scheme.displayNameRes)) },
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
                    Text(stringResource(R.string.settings_tag_based_coloring), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = tagColoringEnabled, onCheckedChange = { viewModel.setTagColoringEnabled(it) })
                }
                AzButton(
                    onClick = onOpenColorEditor,
                    text = stringResource(R.string.settings_customize_per_level_colors),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            SettingsCard(stringResource(R.string.settings_section_filtering)) {
                Text(stringResource(R.string.settings_active_log_levels), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
                    Text(stringResource(R.string.settings_buffer_size), style = MaterialTheme.typography.bodyLarge)
                    Box {
                        OutlinedButton(onClick = { bufferExpanded = true }) { Text(bufferSize.toString()) }
                        DropdownMenu(expanded = bufferExpanded, onDismissRequest = { bufferExpanded = false }) {
                            listOf(1000, 2000, 5000, 10000).forEach { size ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_buffer_lines, size)) },
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
                    Text(stringResource(R.string.settings_reverse_log_order), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isLogReversed, onCheckedChange = { viewModel.setLogReversed(it) })
                }

                AzButton(
                    onClick = onOpenProhibited,
                    text = stringResource(R.string.settings_prohibited_tags, prohibitedCount.size),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )

                Text(
                    stringResource(R.string.settings_log_sources_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                SourceFilterGroup(
                    title = stringResource(R.string.settings_sources),
                    keys = LogSources.SOURCE_BUCKETS,
                    enabled = activeSourceFilters,
                    onToggle = { key, on -> viewModel.setSourceFilterEnabled(key, on) }
                )
                SourceFilterGroup(
                    title = stringResource(R.string.settings_categories),
                    keys = LogSources.CATEGORY_BUCKETS,
                    enabled = activeSourceFilters,
                    onToggle = { key, on -> viewModel.setSourceFilterEnabled(key, on) }
                )
            }

            SettingsCard(stringResource(R.string.settings_section_context)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_context_mode), style = MaterialTheme.typography.bodyLarge)
                        // Non-root foreground detection needs Usage Access; warn if it's on but not granted.
                        if (isContextMode && !isRootEnabled && !isUsageGranted) {
                            Text(
                                stringResource(R.string.context_mode_usage_disabled_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = isContextMode,
                        onCheckedChange = { enable ->
                            // Root detects the foreground app via dumpsys (no grant). Non-root needs Usage
                            // Access: toggle directly if already granted, otherwise show the disclosure
                            // + consent first. Disabling needs no gate.
                            if (enable) {
                                if (isRootEnabled || isUsageGranted) viewModel.toggleContextMode()
                                else showContextDisclosure = true
                            } else viewModel.toggleContextMode()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_root_access), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isRootEnabled, onCheckedChange = { viewModel.setRootEnabled(it) })
                }

                Text(
                    stringResource(R.string.settings_app_monitoring_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                if (monitoredApps.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_apps_pinned),
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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_stop_monitoring, pkg))
                            }
                        }
                    }
                }
                AzButton(
                    onClick = { showAppPicker = true },
                    text = stringResource(R.string.settings_add_app_to_monitor),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            SettingsCard(stringResource(R.string.settings_section_github)) {
            Text(
                stringResource(R.string.settings_github_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Local edit state, seeded from the persisted values; the PAT field is never seeded with
            // the secret — saving writes it to the secure store and the field is cleared.
            var ownerField by remember(githubOwner) { mutableStateOf(githubOwner) }
            var repoField by remember(githubRepo) { mutableStateOf(githubRepo) }
            var tokenField by remember { mutableStateOf("") }
            var showToken by remember { mutableStateOf(false) }
            // OAuth device-flow state (only used when an OAuth client id is configured).
            val githubAuthScope = rememberCoroutineScope()
            var deviceCode by remember { mutableStateOf<GitHubDeviceAuth.DeviceCode?>(null) }
            var authJob by remember { mutableStateOf<Job?>(null) }
            OutlinedTextField(
                value = ownerField,
                onValueChange = { ownerField = it },
                label = { Text(stringResource(R.string.github_owner_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = repoField,
                onValueChange = { repoField = it },
                label = { Text(stringResource(R.string.github_repo_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = tokenField,
                onValueChange = { tokenField = it },
                label = { Text(stringResource(R.string.github_token_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(stringResource(if (showToken) R.string.github_hide else R.string.github_show))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Text(
                stringResource(if (hasGithubToken) R.string.github_token_saved else R.string.github_token_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            AzButton(
                onClick = {
                    viewModel.setGithubOwner(ownerField.trim())
                    viewModel.setGithubRepo(repoField.trim())
                    if (tokenField.isNotBlank()) {
                        viewModel.setGithubToken(tokenField)
                        tokenField = ""
                        showToken = false
                    }
                    Toast.makeText(context, context.getString(R.string.toast_github_saved), Toast.LENGTH_SHORT).show()
                },
                text = stringResource(R.string.github_save),
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            AzButton(
                onClick = {
                    viewModel.setGithubToken(null)
                    tokenField = ""
                    Toast.makeText(context, context.getString(R.string.toast_github_token_cleared), Toast.LENGTH_SHORT).show()
                },
                text = stringResource(R.string.github_clear_token),
                shape = AzButtonShape.RECTANGLE,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )

            // OAuth device-flow sign-in — only when a client id is configured at build time.
            val oauthClientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID
            if (oauthClientId.isNotBlank()) {
                AzButton(
                    onClick = {
                        // Ignore taps while a request/poll is already running (avoids overlapping jobs).
                        if (authJob?.isActive == true) return@AzButton
                        authJob = githubAuthScope.launch {
                            val dc = GitHubDeviceAuth.requestDeviceCode(oauthClientId)
                            if (dc == null) {
                                Toast.makeText(context, context.getString(R.string.toast_github_signin_failed), Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            deviceCode = dc
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            val token = GitHubDeviceAuth.pollForToken(oauthClientId, dc)
                            deviceCode = null
                            if (token != null) {
                                viewModel.setGithubToken(token)
                                Toast.makeText(context, context.getString(R.string.toast_github_signed_in), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_github_signin_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    text = stringResource(R.string.github_oauth_signin),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
            deviceCode?.let { dc ->
                AlertDialog(
                    onDismissRequest = { authJob?.cancel(); deviceCode = null },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(dc.verificationUri))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text(stringResource(R.string.github_oauth_open)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { authJob?.cancel(); deviceCode = null }) { Text(stringResource(R.string.cancel)) }
                    },
                    title = { Text(stringResource(R.string.github_oauth_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.github_oauth_instructions, dc.verificationUri))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(dc.userCode, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.github_oauth_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
            }

            SettingsCard(stringResource(R.string.settings_section_data)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_auto_delete_days), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_auto_delete_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var daysExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { daysExpanded = true }) { Text(autoDeleteDurationDays.toString()) }
                        DropdownMenu(expanded = daysExpanded, onDismissRequest = { daysExpanded = false }) {
                            listOf(0, 1, 3, 7, 14, 30).forEach { days ->
                                DropdownMenuItem(
                                    text = { Text(if (days == 0) stringResource(R.string.settings_never) else stringResource(R.string.settings_days, days)) },
                                    onClick = {
                                        viewModel.setAutoDeleteDurationDays(days)
                                        daysExpanded = false
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_max_log_size), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.settings_max_log_size_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var sizeExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { sizeExpanded = true }) { Text(maxTotalLogSizeMegabytes.toString()) }
                        DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                            listOf(0, 50, 100, 250, 500, 1000).forEach { mb ->
                                DropdownMenuItem(
                                    text = { Text(if (mb == 0) stringResource(R.string.settings_unlimited) else stringResource(R.string.settings_mb, mb)) },
                                    onClick = {
                                        viewModel.setMaxTotalLogSizeMegabytes(mb)
                                        sizeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AzButton(
                    onClick = { exportLauncher.launch("logkitty_prefs.json") },
                    text = stringResource(R.string.settings_export_preferences),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                AzButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    text = stringResource(R.string.settings_import_preferences),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                AzButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(viewModel.exportPreferences()))
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    },
                    text = stringResource(R.string.settings_copy_preferences_json),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                AzButton(
                    onClick = { viewModel.clearLog() },
                    text = stringResource(R.string.settings_clear_log),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                AzButton(
                    onClick = { viewModel.resetLogColors() },
                    text = stringResource(R.string.settings_reset_colors),
                    shape = AzButtonShape.RECTANGLE,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            PrivacyOptionsSlot()

            SettingsCard(stringResource(R.string.settings_section_about)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInfo() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_about_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsFooter(context)

            // Modest trailing margin so the footer clears the persistent bottom ad.
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Resolves a user-facing app label for a package, falling back to the package name. */
private fun appLabel(context: android.content.Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) { pkg }

/**
 * Settings entry that lets the user revisit their ad-consent choices via the UMP privacy-options
 * form (a Google UMP requirement: consent must be changeable later).
 *
 * Rendered only when the `:feature:ads` module is already installed AND UMP reports a privacy-options
 * requirement — so users who never loaded ads, or for whom consent doesn't apply, see nothing (and we
 * never install the module just to show this). The form needs an Activity, which Settings always has
 * (it's hosted by MainActivity); the row is disabled if one can't be resolved.
 */
@Composable
private fun PrivacyOptionsSlot() {
    val handle = rememberFeatureInstall(FeatureModules.ADS)
    if (handle.status !is FeatureInstallStatus.Installed) return
    val context = LocalContext.current
    val feature = remember(context) { FeatureLoader.load<AdsFeature>(FeatureModules.ADS_IMPL, context) } ?: return
    if (!remember(feature, context) { feature.isPrivacyOptionsRequired(context) }) return
    val activity = remember(context) { context.findActivity() }

    SettingsCard(stringResource(R.string.settings_section_privacy)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = activity != null) {
                    activity?.let { act -> feature.showPrivacyOptions(act) {} }
                }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_ad_privacy), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_ad_privacy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Bottom-of-settings footer mirroring AzNavRail's footer: About (repo), Feedback (email),
 * and the author's handle (Instagram).
 */
@Composable
private fun SettingsFooter(context: android.content.Context) {
    fun open(intent: android.content.Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, context.getString(R.string.toast_no_app_to_handle), Toast.LENGTH_SHORT).show() }
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
            // clickable before padding so the whole padded area is the touch target / ripple bounds.
            Text(
                stringResource(R.string.settings_footer_about),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/HereLiesAz/LogKitty")))
                    }
                    .padding(8.dp)
            )
            Text(
                stringResource(R.string.settings_footer_feedback),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_SENDTO,
                            Uri.parse("mailto:hereliesaz@gmail.com?subject=LogKitty")))
                    }
                    .padding(8.dp)
            )
            Text(
                stringResource(R.string.settings_footer_handle),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        open(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://instagram.com/HereLiesAz")))
                    }
                    .padding(8.dp)
            )
        }
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** A single requested permission and whether it is currently granted. */
private data class PermissionStatus(val permission: String, val granted: Boolean)

/**
 * Reads every permission declared in the manifest and resolves its current grant state.
 *
 * Most permissions are answered by the package-info granted flag, but two are special: the overlay
 * permission is an app-op surfaced via [Settings.canDrawOverlays], and usage access is an app-op
 * checked through [android.app.AppOpsManager] — neither is reflected reliably by the grant flag.
 */
private fun collectPermissionStatuses(context: android.content.Context): List<PermissionStatus> {
    val pm = context.packageManager
    val info = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(
                    android.content.pm.PackageManager.GET_PERMISSIONS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        }
    } catch (e: Exception) {
        return emptyList()
    }
    val requested = info.requestedPermissions ?: return emptyList()
    val flags = info.requestedPermissionsFlags ?: IntArray(requested.size)
    return requested.mapIndexed { i, perm ->
        val grantedByFlag = i < flags.size &&
            (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        val granted = when (perm) {
            android.Manifest.permission.SYSTEM_ALERT_WINDOW -> android.provider.Settings.canDrawOverlays(context)
            android.Manifest.permission.PACKAGE_USAGE_STATS -> hasUsageStatsAccess(context)
            else -> grantedByFlag
        }
        PermissionStatus(perm, granted)
    }
}

/** Usage-access (PACKAGE_USAGE_STATS) is an app-op, not a normal grant — check it via AppOps. */
private fun hasUsageStatsAccess(context: android.content.Context): Boolean = try {
    val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    mode == android.app.AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    false
}

/**
 * Returns the most specific system-settings intent for a permission so tapping a row takes the user
 * straight to where they can change it. Permissions with no dedicated screen (normal/install-time
 * grants, or ADB-only ones like READ_LOGS) fall back to the app's details page.
 */
private fun permissionSettingsIntent(context: android.content.Context, permission: String): android.content.Intent {
    val pkgUri = Uri.parse("package:${context.packageName}")
    return when (permission) {
        android.Manifest.permission.SYSTEM_ALERT_WINDOW ->
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        android.Manifest.permission.PACKAGE_USAGE_STATS ->
            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
        android.Manifest.permission.POST_NOTIFICATIONS ->
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        else ->
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }
}

/** Opens the system settings screen for a permission, with an OEM fallback for the overlay one. */
private fun openPermissionSettings(context: android.content.Context, permission: String) {
    runCatching { context.startActivity(permissionSettingsIntent(context, permission)) }
        .onFailure {
            // Some OEM ROMs reject ACTION_MANAGE_OVERLAY_PERMISSION with a package: URI — retry
            // without it (opens the general overlay list) before giving up.
            val fallback = if (permission == android.Manifest.permission.SYSTEM_ALERT_WINDOW) {
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            } else null
            val recovered = fallback != null && runCatching { context.startActivity(fallback) }.isSuccess
            if (!recovered) {
                Toast.makeText(context, context.getString(R.string.toast_no_app_to_handle), Toast.LENGTH_SHORT).show()
            }
        }
}

/** Popup explaining why LogKitty uses a permission, with a shortcut to its system settings screen. */
@Composable
private fun PermissionInfoDialog(
    title: String,
    body: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.perm_open_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.perm_close)) }
        }
    )
}

/** Why LogKitty requests each permission, shown in the per-permission info popup. */
@Composable
private fun permissionJustification(permission: String): String {
    val res = when (permission) {
        "android.permission.POST_NOTIFICATIONS" -> R.string.perm_why_post_notifications
        "android.permission.INTERNET" -> R.string.perm_why_internet
        "android.permission.PACKAGE_USAGE_STATS" -> R.string.perm_why_package_usage_stats
        "android.permission.FOREGROUND_SERVICE" -> R.string.perm_why_foreground_service
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" -> R.string.perm_why_foreground_service_special
        "android.permission.SYSTEM_ALERT_WINDOW" -> R.string.perm_why_system_alert_window
        "android.permission.READ_LOGS" -> R.string.perm_why_read_logs
        "android.permission.QUERY_ALL_PACKAGES" -> R.string.perm_why_query_all_packages
        "com.google.android.gms.permission.AD_ID" -> R.string.perm_why_ad_id
        else -> 0
    }
    return if (res != 0) stringResource(res) else permission
}

/** Maps a permission name to a friendly, localized label; unknown permissions show their short name. */
@Composable
private fun permissionLabel(permission: String): String {
    val res = when (permission) {
        "android.permission.POST_NOTIFICATIONS" -> R.string.perm_post_notifications
        "android.permission.INTERNET" -> R.string.perm_internet
        "android.permission.PACKAGE_USAGE_STATS" -> R.string.perm_package_usage_stats
        "android.permission.FOREGROUND_SERVICE" -> R.string.perm_foreground_service
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" -> R.string.perm_foreground_service_special
        "android.permission.SYSTEM_ALERT_WINDOW" -> R.string.perm_system_alert_window
        "android.permission.READ_LOGS" -> R.string.perm_read_logs
        "android.permission.QUERY_ALL_PACKAGES" -> R.string.perm_query_all_packages
        "com.google.android.gms.permission.AD_ID" -> R.string.perm_ad_id
        else -> 0
    }
    return if (res != 0) stringResource(res) else permission.substringAfterLast('.')
}

/**
 * Lists every permission the app declares and whether it is currently granted. Re-reads grant state
 * on ON_RESUME so it updates after the user grants something in system settings and returns.
 */
@Composable
private fun PermissionsSection(context: android.content.Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<PermissionStatus>>(emptyList()) }
    // Resolve grant state off the main thread (binder IPC); recompute whenever the screen resumes
    // (addObserver replays ON_RESUME to a new observer, so this also drives the initial load).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    statuses = withContext(Dispatchers.IO) { collectPermissionStatuses(context) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tapping a row opens a popup explaining why LogKitty uses that permission, with a button to
    // jump to the relevant system settings screen.
    var infoPermission by remember { mutableStateOf<String?>(null) }
    infoPermission?.let { perm ->
        PermissionInfoDialog(
            title = permissionLabel(perm),
            body = permissionJustification(perm),
            onOpenSettings = {
                openPermissionSettings(context, perm)
                infoPermission = null
            },
            onDismiss = { infoPermission = null }
        )
    }

    SettingsSectionHeader(stringResource(R.string.settings_section_permissions))
    statuses.forEach { status ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { infoPermission = status.permission }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                permissionLabel(status.permission),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(if (status.granted) R.string.perm_granted else R.string.perm_not_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Prominent disclosure shown before Context Mode is enabled on a non-root device, since that feature
 * uses Usage Access to detect the foreground app. Explains what is accessed (which app is in the
 * foreground), why, and that no screen content / personal data is read — required by Google Play's
 * User Data policy.
 */
@Composable
private fun ContextModeDisclosureDialog(onDismiss: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
        text = { Text(stringResource(R.string.accessibility_disclosure_body)) },
        confirmButton = {
            TextButton(onClick = onAgree) { Text(stringResource(R.string.accessibility_disclosure_agree)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * A visually distinct "compartment" for a group of related settings: a [Card] with an optional
 * section title rendered via [SettingsSectionHeader]. Pass `title = null` when the content renders
 * its own header (e.g. [PermissionsSection]) to avoid a duplicate.
 */
@Composable
private fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) SettingsSectionHeader(title)
            content()
        }
    }
}

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
