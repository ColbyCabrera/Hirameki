package com.ichi2.anki.multimedia.audio.ui.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.audio.AudioRecorderViewModel
import com.ichi2.anki.multimedia.audio.AudioWaveformCompose
import com.ichi2.anki.noteeditor.compose.NoteEditorTopAppBar
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.compose.theme.RobotoMono
import java.util.Locale

@Composable
fun AudioRecorderScreen(
    title: String,
    onBackClick: () -> Unit,
    viewModel: AudioRecorderViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AudioRecorderContent(
        title = title,
        onBackClick = onBackClick,
        uiState = uiState,
        onIntent = viewModel::processIntent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioRecorderContent(
    title: String,
    onBackClick: () -> Unit,
    uiState: AudioRecorderViewModel.UiState,
    onIntent: (AudioRecorderViewModel.Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(), topBar = {
            NoteEditorTopAppBar(
                title = title,
                onBackClick = onBackClick,
                showSaveAction = false,
                showPreviewAction = false
            )
        }, containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Delete / Top Right

            Surface(
                Modifier
                    .weight(1f)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (uiState.state != AudioRecorderViewModel.RecordingState.Idle) {
                            IconButton(onClick = { onIntent(AudioRecorderViewModel.Intent.DiscardRecording) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete_24px),
                                    contentDescription = stringResource(R.string.delete_note_message),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    AudioWaveformCompose(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        amplitudes = uiState.amplitudes,
                        showAmplitudes = uiState.state != AudioRecorderViewModel.RecordingState.Idle,
                        currentIndex = if (uiState.state in listOf(
                                AudioRecorderViewModel.RecordingState.Playing,
                                AudioRecorderViewModel.RecordingState.PlaybackPaused,
                                AudioRecorderViewModel.RecordingState.PlaybackReady
                            )
                        ) {
                            (uiState.playbackProgressMillis / uiState.amplitudeSampleMs).toInt()
                        } else {
                            -1
                        }
                    )
                }
            }

            // Text / Time
            Text(text = formatDuration(uiState.durationMillis.takeIf {
                uiState.state in listOf(
                    AudioRecorderViewModel.RecordingState.Recording,
                    AudioRecorderViewModel.RecordingState.RecordingPaused,
                    AudioRecorderViewModel.RecordingState.PlaybackReady
                )
            } ?: uiState.playbackProgressMillis),
                fontFamily = RobotoMono,
                fontSize = 84.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp))

            // Controls
            ButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
                expandedRatio = 0.05f,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                overflowIndicator = {}) {
                when (uiState.state) {
                    AudioRecorderViewModel.RecordingState.Idle -> {
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            RecordButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interactionSource),
                                onClick = { onIntent(AudioRecorderViewModel.Intent.StartRecording) },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            SaveButton(
                                modifier = Modifier.animateWidth(interactionSource),
                                enabled = false,
                                onClick = { },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                    }

                    in listOf(
                        AudioRecorderViewModel.RecordingState.Recording,
                        AudioRecorderViewModel.RecordingState.RecordingPaused
                    ) -> {
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            PauseResumeButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interactionSource),
                                isPaused = uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused,
                                onClick = {
                                    if (uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused) {
                                        onIntent(AudioRecorderViewModel.Intent.ResumeRecording)
                                    } else {
                                        onIntent(AudioRecorderViewModel.Intent.PauseRecording)
                                    }
                                },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            StopButton(
                                modifier = Modifier.animateWidth(interactionSource),
                                onClick = { onIntent(AudioRecorderViewModel.Intent.StopRecording) },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            SaveButton(
                                modifier = Modifier.animateWidth(interactionSource),
                                enabled = uiState.isSaveEnabled,
                                onClick = { onIntent(AudioRecorderViewModel.Intent.SaveRecording) },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                    }

                    in listOf(
                        AudioRecorderViewModel.RecordingState.PlaybackReady,
                        AudioRecorderViewModel.RecordingState.Playing,
                        AudioRecorderViewModel.RecordingState.PlaybackPaused
                    ) -> {
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            PlayPauseButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateWidth(interactionSource),
                                isPlaying = uiState.state == AudioRecorderViewModel.RecordingState.Playing,
                                onClick = {
                                    if (uiState.state == AudioRecorderViewModel.RecordingState.Playing) {
                                        onIntent(AudioRecorderViewModel.Intent.PausePlayback)
                                    } else {
                                        onIntent(AudioRecorderViewModel.Intent.StartPlayback)
                                    }
                                },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                        customItem(buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            SaveButton(
                                modifier = Modifier.animateWidth(interactionSource),
                                enabled = uiState.isSaveEnabled,
                                onClick = { onIntent(AudioRecorderViewModel.Intent.SaveRecording) },
                                interactionSource = interactionSource,
                            )
                        }, menuContent = { })
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shapes: ButtonShapes = ButtonDefaults.shapes(),
) {
    Button(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.size(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_record),
            contentDescription = stringResource(R.string.record_voice),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = CircleShape
) {
    Button(
        onClick = onClick,
        shape = shape,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_stop),
            contentDescription = stringResource(R.string.stop_recording),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = stringResource(R.string.stop_recording),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PauseResumeButton(
    isPaused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shapes: ButtonShapes = ButtonDefaults.shapes()
) {
    Button(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Icon(
            painter = painterResource(id = if (isPaused) R.drawable.round_play_arrow_24 else R.drawable.round_pause_24),
            contentDescription = if (isPaused) stringResource(R.string.play_recording) else stringResource(
                R.string.pause_playback
            ),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = if (isPaused) stringResource(R.string.resume) else stringResource(R.string.pause_playback),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shapes: ButtonShapes = ButtonDefaults.shapes()
) {
    Button(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Icon(
            painter = painterResource(id = if (isPlaying) R.drawable.round_pause_24 else R.drawable.round_play_arrow_24),
            contentDescription = if (isPlaying) stringResource(R.string.pause_playback) else stringResource(
                R.string.play_recording
            ),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = if (isPlaying) stringResource(R.string.pause_playback) else stringResource(R.string.play_recording),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}


@Composable
fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = CircleShape
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier.size(64.dp),
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.save_24px),
            contentDescription = stringResource(R.string.save),
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val deciseconds = (millis % 1000) / 100
    return String.format(Locale.ROOT, "%02d:%02d.%d", minutes, seconds, deciseconds)
}

@Preview(name = "Idle State", showBackground = true)
@Composable
private fun AudioRecorderScreenIdlePreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Idle, amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@Preview(name = "Recording State", showBackground = true)
@Composable
private fun AudioRecorderScreenRecordingPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Recording,
                durationMillis = 12300L,
                isSaveEnabled = true,
                amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@Preview(name = "Recording Paused", showBackground = true)
@Composable
private fun AudioRecorderScreenRecordingPausedPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.RecordingPaused,
                durationMillis = 15000L,
                isSaveEnabled = true,
                amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@Preview(name = "Playback Ready State", showBackground = true)
@Composable
private fun AudioRecorderScreenPlaybackReadyPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.PlaybackReady,
                durationMillis = 30000L,
                playbackProgressMillis = 0L,
                isSaveEnabled = true,
                amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@Preview(name = "Playing State", showBackground = true)
@Composable
private fun AudioRecorderScreenPlayingPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Playing,
                durationMillis = 30000L,
                playbackProgressMillis = 10500L,
                isSaveEnabled = true,
                amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@Preview(name = "Playback Paused", showBackground = true)
@Composable
private fun AudioRecorderScreenPlaybackPausedPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            title = "Audio Recorder", onBackClick = {}, uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.PlaybackPaused,
                durationMillis = 30000L,
                playbackProgressMillis = 10500L,
                isSaveEnabled = true,
                amplitudes = listOf(
                    0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f, 0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f
                )
            ), onIntent = {})
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Buttons Overview", showBackground = true)
@Composable
private fun AudioRecorderButtonsPreview() {
    AnkiDroidTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RecordButton(onClick = {})
                    StopButton(onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PauseResumeButton(isPaused = false, onClick = {})
                    PauseResumeButton(isPaused = true, onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlayPauseButton(isPlaying = false, onClick = {})
                    PlayPauseButton(isPlaying = true, onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SaveButton(enabled = true, onClick = {})
                    SaveButton(enabled = false, onClick = {})
                }
            }
        }
    }
}
