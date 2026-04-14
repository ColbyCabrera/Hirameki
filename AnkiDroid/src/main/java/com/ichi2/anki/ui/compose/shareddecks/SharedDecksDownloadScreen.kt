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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.RobotoMono

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Downloading : DownloadStatus
    data object WaitingForNetwork : DownloadStatus
    data object Failed : DownloadStatus
    data object Complete : DownloadStatus
}

data class DownloadUiState(
    val fileName: String = "",
    val progress: Float = 0f,
    val progressText: String = "0%",
    val status: DownloadStatus = DownloadStatus.Idle
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DownloadHero(status = state.status)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (state.status) {
                        DownloadStatus.Failed -> stringResource(R.string.download_failed)
                        DownloadStatus.Complete -> stringResource(R.string.import_deck)
                        else -> stringResource(R.string.downloading_file, state.fileName)
                    },
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    textAlign = TextAlign.Center,
                    color = if (state.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.deck_download_progress_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), contentAlignment = Alignment.Center
            ) {
                DownloadProgressSection(state = state)
            }

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
private fun DownloadHero(status: DownloadStatus) {
    val containerColor = when (status) {
        DownloadStatus.Failed -> MaterialTheme.colorScheme.errorContainer
        DownloadStatus.Complete -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val icon = when (status) {
        DownloadStatus.Failed -> R.drawable.error_24px
        DownloadStatus.Complete -> R.drawable.ic_done_white
        else -> R.drawable.download_24px
    }

    val iconTint = when (status) {
        DownloadStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.Complete -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val shape = when (status) {
        DownloadStatus.Failed -> RoundedPolygonShape(MaterialShapes.Triangle)
        else -> RoundedPolygonShape(MaterialShapes.Cookie4Sided)
    }

    Box(
        modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center
    ) {

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp), shape = shape, color = containerColor
        ) {}
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
        targetValue = state.progress / 100f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow
        ), label = "DownloadProgress"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(20.dp), contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .graphicsLayer(
                    rotationZ = animatedProgress * 360f
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                    shape = RoundedPolygonShape(MaterialShapes.Arrow)
                )
        )

        // Pulsing technical wavy ring
        CircularWavyProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            progress = { animatedProgress },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            color = if (state.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        val isHalted =
            state.status == DownloadStatus.Failed || state.status == DownloadStatus.WaitingForNetwork
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (state.status) {
                    DownloadStatus.WaitingForNetwork -> stringResource(R.string.download_status_waiting_for_network)
                    else -> stringResource(R.string.download_status_downloading)
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = RobotoMono,
                fontWeight = FontWeight.Bold,
                color = if (isHalted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.8f
                )
            )
            Text(
                text = state.progressText,
                fontFamily = RobotoMono,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 64.sp,
                color = if (state.status == DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isHalted) stringResource(R.string.download_status_halted) else stringResource(
                    R.string.download_status_active
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = RobotoMono,
                color = if (isHalted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            visible = state.status == DownloadStatus.Complete, enter = fadeIn(), exit = fadeOut()
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
            visible = state.status == DownloadStatus.Failed, enter = fadeIn(), exit = fadeOut()
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
            visible = state.status != DownloadStatus.Complete && state.status != DownloadStatus.Failed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    contentColor = MaterialTheme.colorScheme.onError,
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.cancel_download),
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
            fileName = "Medical Terminology.apkg",
            progress = 45f,
            progressText = "45.2%",
            status = DownloadStatus.Downloading
        ), onCancel = {}, onRetry = {}, onImport = {}, onOpenInBrowser = {})
    }
}

@Preview
@Composable
fun SharedDecksDownloadScreenFailedPreview() {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = DownloadUiState(
            fileName = "Medical Terminology.apkg", status = DownloadStatus.Failed
        ), onCancel = {}, onRetry = {}, onImport = {}, onOpenInBrowser = {})
    }
}

@Preview
@Composable
fun SharedDecksDownloadScreenCompletePreview() {
    AnkiDroidTheme {
        SharedDecksDownloadScreen(
            state = DownloadUiState(
            fileName = "Medical Terminology.apkg",
            progress = 100f,
            progressText = "100%",
            status = DownloadStatus.Complete
        ), onCancel = {}, onRetry = {}, onImport = {}, onOpenInBrowser = {})
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadProgressSectionPreview() {
    AnkiDroidTheme {
        DownloadProgressSection(
            state = DownloadUiState(
                progress = 75f, progressText = "75%", status = DownloadStatus.Downloading
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DownloadProgressSectionWaitingPreview() {
    AnkiDroidTheme {
        DownloadProgressSection(
            state = DownloadUiState(
                progress = 0f, progressText = "0%", status = DownloadStatus.WaitingForNetwork
            )
        )
    }
}
