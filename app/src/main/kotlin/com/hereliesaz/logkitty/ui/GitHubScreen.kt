package com.hereliesaz.logkitty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hereliesaz.logkitty.R

/**
 * Full-screen GitHub Actions host (launched from the dashboard). Reuses the same [GitHubFeatureSlot]
 * the overlay tab hosts, so the runs → jobs → job-log drill-down, live polling, and watch
 * notifications all behave identically here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(viewModel: MainViewModel, onBack: () -> Unit, onConfigure: () -> Unit) {
    // System back / gesture returns to the dashboard rather than exiting the activity.
    BackHandler(onBack = onBack)
    val owner by viewModel.githubOwner.collectAsState()
    val repo by viewModel.githubRepo.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_github_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        GitHubFeatureSlot(
            owner = owner,
            repo = repo,
            tokenProvider = { viewModel.readGithubToken() },
            onConfigure = onConfigure,
            fontFamily = null,
            fontSize = fontSize,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
