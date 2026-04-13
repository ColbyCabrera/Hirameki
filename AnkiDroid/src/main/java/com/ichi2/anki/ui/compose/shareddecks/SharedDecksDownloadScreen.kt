/*
 *  Copyright (c) 2026 AnkiDroid
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose.shareddecks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.RobotoMono


data class DownloadUiState(
    val fileName: String = "",
    val progress: Float = 0f,
    val progressText: String = "0%",
    val isDownloading: Boolean = true,
    val isFailed: Boolean = false,
    val isWaitingForNetwork: Boolean = false,
    val isComplete: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedDecksDownloadScreen(
    state: DownloadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0), topBar = {
            TopAppBar(title = { }, navigationIcon = {
                // Back handled by the fragment's onBackPressedCallback or cancel button
            })
        }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top section: Hero and Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DownloadHero(isFailed = state.isFailed, isComplete = state.isComplete)

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (state.isFailed) {
                        stringResource(R.string.download_failed)
                    } else if (state.isComplete) {
                        stringResource(R.string.import_deck)
                    } else {
                        stringResource(R.string.downloading_file, state.fileName)
                    },
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    textAlign = TextAlign.Center,
                    color = if (state.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.deck_download_progress_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Center section: Progress
            DownloadProgressSection(state = state)

            Spacer(modifier = Modifier.weight(1f))

            // Bottom section: Actions
            DownloadActions(
                state = state,
                onCancel = onCancel,
                onRetry = onRetry,
                onImport = onImport,
                onOpenInBrowser = onOpenInBrowser
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadHero(isFailed: Boolean, isComplete: Boolean) {
    val containerColor = when {
        isFailed -> MaterialTheme.colorScheme.errorContainer
        isComplete -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val icon = when {
        isFailed -> R.drawable.error_24px
        isComplete -> R.drawable.ic_done_white
        else -> R.drawable.download_24px
    }

    val iconTint = when {
        isFailed -> MaterialTheme.colorScheme.onErrorContainer
        isComplete -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = if (isFailed) {
        RoundedPolygonShape(MaterialShapes.SoftBoom)
    } else {
        RoundedPolygonShape(MaterialShapes.Cookie4Sided)
    }

    Box(
        modifier = Modifier
            .size(120.dp)
            .background(containerColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = iconTint
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadProgressSection(state: DownloadUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress / 100f, label = "DownloadProgress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = state.progressText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (state.isWaitingForNetwork) {
                Text(
                    text = stringResource(R.string.check_network),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = RobotoMono,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadActions(
    state: DownloadUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onImport: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = state.isComplete, enter = fadeIn(), exit = fadeOut()
        ) {
            Button(
                onClick = onImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    text = stringResource(R.string.import_deck),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AnimatedVisibility(
            visible = state.isFailed, enter = fadeIn(), exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        text = stringResource(R.string.try_again),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        text = stringResource(R.string.open_in_browser),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !state.isComplete && !state.isFailed, enter = fadeIn(), exit = fadeOut()
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    text = stringResource(R.string.cancel_download),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview
@Composable
fun SharedDecksDownloadScreenPreview() {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = DownloadUiState(
            fileName = "Medical Terminology.apkg", progress = 45f, progressText = "45.2%"
        ), onCancel = {}, onRetry = {}, onImport = {}, onOpenInBrowser = {})
    }
}

@Preview
@Composable
fun SharedDecksDownloadScreenFailedPreview() {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = DownloadUiState(
            fileName = "Medical Terminology.apkg", isDownloading = false, isFailed = true
        ), onCancel = {}, onRetry = {}, onImport = {}, onOpenInBrowser = {})
    }
}
