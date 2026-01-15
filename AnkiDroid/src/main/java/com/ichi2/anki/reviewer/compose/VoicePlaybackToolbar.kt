/****************************************************************************************
 * Copyright (c) 2024 AnkiDroid Open Source Team                                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.reviewer.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.VoicePlaybackViewModel
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * Compose UI for the Voice Playback (mic toolbar) in the Reviewer.
 */
@Composable
fun VoicePlaybackToolbar(
    viewModel: VoicePlaybackViewModel,
    onToggleRecording: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()

    VoicePlaybackToolbarContent(
        state = state,
        amplitude = amplitude,
        onToggleRecording = onToggleRecording,
        onTogglePlayback = viewModel::togglePlayback,
        onDiscardRecording = viewModel::discardRecording,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoicePlaybackToolbarContent(
    state: VoicePlaybackViewModel.RecordingState,
    amplitude: Float,
    onToggleRecording: () -> Unit,
    onTogglePlayback: () -> Unit,
    onDiscardRecording: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAmplitude by animateFloatAsState(targetValue = amplitude, label = "amplitude")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Waveform / Progress indicator area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is VoicePlaybackViewModel.RecordingState.Recording -> {
                        // Simple amplitude visualization
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(fraction = 0.1f + (animatedAmplitude * 0.9f))
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.extraExtraLarge)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        )
                    }

                    is VoicePlaybackViewModel.RecordingState.Playing -> {
                        val progress = state.progress
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }

                    else -> {
                        // Idle or PlaybackReady - show placeholder
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraExtraLarge)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }

            // Primary action button (Record/Stop/Play)
            FilledIconButton(
                onClick = {
                    when (state) {
                        is VoicePlaybackViewModel.RecordingState.Idle, is VoicePlaybackViewModel.RecordingState.Recording -> onToggleRecording()
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady, is VoicePlaybackViewModel.RecordingState.Playing -> onTogglePlayback()
                    }
                },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = when (state) {
                        is VoicePlaybackViewModel.RecordingState.Recording -> MaterialTheme.colorScheme.error
                        else -> IconButtonDefaults.filledIconButtonColors().containerColor
                    }, contentColor = when (state) {
                        is VoicePlaybackViewModel.RecordingState.Recording -> MaterialTheme.colorScheme.onError
                        else -> IconButtonDefaults.filledIconButtonColors().contentColor
                    }
                )
            ) {
                Icon(
                    imageVector = when (state) {
                        is VoicePlaybackViewModel.RecordingState.Idle -> Icons.Default.Mic
                        is VoicePlaybackViewModel.RecordingState.Recording -> Icons.Default.Stop
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady -> Icons.Default.PlayArrow
                        is VoicePlaybackViewModel.RecordingState.Playing -> Icons.Default.Pause
                    },
                    contentDescription = when (state) {
                        is VoicePlaybackViewModel.RecordingState.Idle -> stringResource(R.string.start_recording)
                        is VoicePlaybackViewModel.RecordingState.Recording -> stringResource(R.string.stop_recording)
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady -> stringResource(R.string.play_recording)
                        is VoicePlaybackViewModel.RecordingState.Playing -> stringResource(R.string.pause_playback)
                    },
                )
            }

            // Secondary action (Discard or Close)
            FilledTonalIconButton(
                modifier = Modifier.size(48.dp), onClick = {
                    when (state) {
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady, is VoicePlaybackViewModel.RecordingState.Playing -> onDiscardRecording()
                        else -> onDismiss()
                    }
                }) {
                Icon(
                    painter = when (state) {
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady, is VoicePlaybackViewModel.RecordingState.Playing -> painterResource(
                            R.drawable.delete_24px
                        )

                        else -> painterResource(R.drawable.close_24px)
                    },
                    contentDescription = when (state) {
                        is VoicePlaybackViewModel.RecordingState.PlaybackReady, is VoicePlaybackViewModel.RecordingState.Playing -> stringResource(R.string.discard)
                        else -> stringResource(R.string.dialog_cancel)
                    },
                )
            }
        }
    }
}

@Preview(name = "Idle", showBackground = true)
@Composable
private fun VoicePlaybackToolbarPreview_Idle() {
    AnkiDroidTheme {
        VoicePlaybackToolbarContent(
            state = VoicePlaybackViewModel.RecordingState.Idle,
            amplitude = 0f,
            onToggleRecording = {},
            onTogglePlayback = {},
            onDiscardRecording = {},
            onDismiss = {})
    }
}

@Preview(name = "Recording", showBackground = true)
@Composable
private fun VoicePlaybackToolbarPreview_Recording() {
    AnkiDroidTheme {
        VoicePlaybackToolbarContent(
            state = VoicePlaybackViewModel.RecordingState.Recording,
            amplitude = 0.5f,
            onToggleRecording = {},
            onTogglePlayback = {},
            onDiscardRecording = {},
            onDismiss = {})
    }
}

@Preview(name = "Playback Ready", showBackground = true)
@Composable
private fun VoicePlaybackToolbarPreview_PlaybackReady() {
    AnkiDroidTheme {
        VoicePlaybackToolbarContent(
            state = VoicePlaybackViewModel.RecordingState.PlaybackReady,
            amplitude = 0f,
            onToggleRecording = {},
            onTogglePlayback = {},
            onDiscardRecording = {},
            onDismiss = {})
    }
}

@Preview(name = "Playing", showBackground = true)
@Composable
private fun VoicePlaybackToolbarPreview_Playing() {
    AnkiDroidTheme {
        VoicePlaybackToolbarContent(
            state = VoicePlaybackViewModel.RecordingState.Playing(0.4f),
            amplitude = 0f,
            onToggleRecording = {},
            onTogglePlayback = {},
            onDiscardRecording = {},
            onDismiss = {})
    }
}
