package com.hereliesaz.logkitty.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hereliesaz.logkitty.R
import com.hereliesaz.logkitty.ui.markdown.MdBlock
import com.hereliesaz.logkitty.ui.markdown.MarkdownText
import com.hereliesaz.logkitty.ui.markdown.parseMarkdownBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app reader for the project's own documentation. Shows an index of the docs bundled into the
 * app's assets (README, Privacy Policy, Permissions, Code of Conduct); selecting one renders its
 * Markdown natively. The documents are copied from the repo's `README.md` / `docs/` at build time
 * (see the `copyInAppDocs` task in app/build.gradle.kts), so there's a single source of truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<InfoDoc?>(null) }

    // While viewing a document, the system back gesture returns to the index rather than leaving.
    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(selected?.titleRes ?: R.string.info_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val doc = selected) {
            null -> InfoIndex(padding = padding, onOpen = { selected = it })
            else -> DocumentContent(doc = doc, padding = padding, onLinkClick = { url -> openUrl(context, url) })
        }
    }
}

@Composable
private fun InfoIndex(padding: PaddingValues, onOpen: (InfoDoc) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        InfoDocuments.forEach { doc ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onOpen(doc) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(doc.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(doc.descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentContent(doc: InfoDoc, padding: PaddingValues, onLinkClick: (String) -> Unit) {
    val context = LocalContext.current
    val state by produceState<DocState>(initialValue = DocState.Loading, doc) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("docs/${doc.asset}").bufferedReader().use { it.readText() }
            }.fold(
                onSuccess = { DocState.Loaded(parseMarkdownBlocks(it)) },
                onFailure = { DocState.Error },
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (val s = state) {
            DocState.Loading -> Text(stringResource(R.string.info_loading), style = MaterialTheme.typography.bodyMedium)
            DocState.Error -> Text(stringResource(R.string.info_load_error), style = MaterialTheme.typography.bodyMedium)
            is DocState.Loaded -> MarkdownText(blocks = s.blocks, onLinkClick = onLinkClick)
        }
    }
}

private sealed interface DocState {
    data object Loading : DocState
    data object Error : DocState
    data class Loaded(val blocks: List<MdBlock>) : DocState
}

private data class InfoDoc(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val asset: String,
)

private val InfoDocuments = listOf(
    InfoDoc(R.string.info_doc_readme, R.string.info_doc_readme_desc, "README.md"),
    InfoDoc(R.string.info_doc_privacy, R.string.info_doc_privacy_desc, "PRIVACY_POLICY.md"),
    InfoDoc(R.string.info_doc_permissions, R.string.info_doc_permissions_desc, "PERMISSIONS.md"),
    InfoDoc(R.string.info_doc_conduct, R.string.info_doc_conduct_desc, "conduct.md"),
)

/** Opens a URL with the app's standard pattern: an ACTION_VIEW intent, toast on failure. */
private fun openUrl(context: Context, url: String) {
    runCatching {
        // NEW_TASK so the link still opens if the context is ever non-Activity (matches the app's
        // other launch sites in LogBottomSheet / the GitHub OAuth flow).
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_no_app_to_handle), Toast.LENGTH_SHORT).show()
    }
}
