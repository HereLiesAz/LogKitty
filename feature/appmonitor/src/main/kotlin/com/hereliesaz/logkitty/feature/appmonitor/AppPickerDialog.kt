package com.hereliesaz.logkitty.feature.appmonitor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.hereliesaz.logkitty.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A lightweight description of an installed application, used by [AppPickerDialog]. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Lists every installed package via `su -c pm list packages`, or `null` when root isn't available
 * (so the caller falls back to the launcher-visible PackageManager list). Lets rooted users keep the
 * full app list after QUERY_ALL_PACKAGES was dropped.
 */
private fun rootListPackages(): List<String>? = try {
    val process = ProcessBuilder("su", "-c", "pm list packages").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val finished = process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)
    if (!finished) process.destroyForcibly()
    val pkgs = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
    pkgs.ifEmpty { null }
} catch (e: Exception) {
    null
}

/**
 * A dialog that lists installed applications (label + icon, searchable) and returns the package
 * name of the one the user taps. Used by [SettingsScreen] to pin an app for a dedicated,
 * UID-filtered log tab. Requires `QUERY_ALL_PACKAGES` (declared in the manifest).
 */
@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // Load (and label-resolve) the installed apps off the main thread. Without QUERY_ALL_PACKAGES,
    // PackageManager only sees apps matching the manifest <queries> (launchable apps) — enough for
    // the picker. On a rooted device we list every package via `pm list packages` so root users keep
    // the full list; labels are resolved where visible, otherwise the package name is shown.
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            fun labelFor(pkg: String): String =
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

            val rootPkgs = rootListPackages()
            if (rootPkgs != null) {
                rootPkgs.map { InstalledApp(it, labelFor(it)) }.sortedBy { it.label.lowercase() }
            } else {
                runCatching {
                    pm.getInstalledApplications(0)
                        .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
        }
    }

    val filtered = remember(query, apps) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.app_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.app_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(8.dp))

                if (apps == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app = app, onClick = { onAppSelected(app.packageName) })
                        }
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    // Lazily decode the icon for the visible row only.
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp)) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
