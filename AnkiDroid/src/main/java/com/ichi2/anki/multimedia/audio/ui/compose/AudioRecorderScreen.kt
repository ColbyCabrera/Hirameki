package com.ichi2.anki.multimedia.audio.ui.compose

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.audio.AudioRecorderViewModel
import com.ichi2.anki.multimedia.audio.AudioWaveformCompose
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import java.util.Locale

@Composable
fun AudioRecorderScreen(viewModel: AudioRecorderViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AudioRecorderContent(
        uiState = uiState, onIntent = viewModel::processIntent
    )
}

@Composable
fun AudioRecorderContent(
    uiState: AudioRecorderViewModel.UiState, onIntent: (AudioRecorderViewModel.Intent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // Use Material3 surface color
    ) {
        Column(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Delete / Top Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (uiState.state != AudioRecorderViewModel.RecordingState.Idle) {
                    IconButton(onClick = { onIntent(AudioRecorderViewModel.Intent.DiscardRecording) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_note_message),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.state in listOf(
                    AudioRecorderViewModel.RecordingState.Recording,
                    AudioRecorderViewModel.RecordingState.RecordingPaused
                )
            ) {
                AudioWaveformCompose(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    amplitudes = uiState.amplitudes,
                    isRecording = uiState.state == AudioRecorderViewModel.RecordingState.Recording
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Text / Time
            Text(text = formatDuration(uiState.durationMillis.takeIf {
                uiState.state in listOf(
                    AudioRecorderViewModel.RecordingState.Recording,
                    AudioRecorderViewModel.RecordingState.RecordingPaused
                )
            } ?: uiState.playbackProgressMillis),
                fontSize = 48.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp))

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (uiState.state) {
                    AudioRecorderViewModel.RecordingState.Idle -> {
                        Spacer(modifier = Modifier.weight(1f))
                        RecordButton(
                            onClick = { onIntent(AudioRecorderViewModel.Intent.StartRecording) })
                        Spacer(modifier = Modifier.weight(1f))
                        SaveButton(
                            enabled = false, onClick = { })
                    }

                    in listOf(
                        AudioRecorderViewModel.RecordingState.Recording,
                        AudioRecorderViewModel.RecordingState.RecordingPaused
                    ) -> {
                        PauseResumeButton(
                            isPaused = uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused,
                            onClick = {
                                if (uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused) {
                                    onIntent(AudioRecorderViewModel.Intent.ResumeRecording)
                                } else {
                                    onIntent(AudioRecorderViewModel.Intent.PauseRecording)
                                }
                            })
                        StopButton(
                            onClick = { onIntent(AudioRecorderViewModel.Intent.StopRecording) })
                        SaveButton(
                            enabled = false, onClick = { })
                    }

                    in listOf(
                        AudioRecorderViewModel.RecordingState.PlaybackReady,
                        AudioRecorderViewModel.RecordingState.Playing,
                        AudioRecorderViewModel.RecordingState.PlaybackPaused
                    ) -> {
                        PlayPauseButton(
                            isPlaying = uiState.state == AudioRecorderViewModel.RecordingState.Playing,
                            onClick = {
                                if (uiState.state == AudioRecorderViewModel.RecordingState.Playing) {
                                    onIntent(AudioRecorderViewModel.Intent.PausePlayback)
                                } else {
                                    onIntent(AudioRecorderViewModel.Intent.StartPlayback)
                                }
                            })
                        Spacer(modifier = Modifier.weight(1f))
                        SaveButton(
                            enabled = uiState.isSaveEnabled,
                            onClick = { onIntent(AudioRecorderViewModel.Intent.SaveRecording) })
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun RecordButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_record),
            contentDescription = stringResource(R.string.record_voice),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun StopButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape, // Adjust for pill shape if needed, screenshot shows pill shape for stop/pause
        modifier = Modifier
            .height(80.dp)
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_stop), // Need stop icon
            contentDescription = stringResource(R.string.stop_recording),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
            text = stringResource(R.string.stop_recording),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp
        )
    }
}

@Composable
fun PauseResumeButton(isPaused: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape, // Pill
        modifier = Modifier
            .height(80.dp)
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Icon(
            painter = painterResource(id = if (isPaused) R.drawable.round_play_arrow_24 else R.drawable.round_pause_24), // Ensure icons
            contentDescription = if (isPaused) stringResource(R.string.play_recording) else stringResource(
                R.string.pause_playback
            ), tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = if (isPaused) stringResource(R.string.play_recording) else stringResource(R.string.pause_playback),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp
        )
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape, // Pill
        modifier = Modifier
            .height(80.dp)
            .padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
            fontSize = 18.sp
        )
    }
}


@Composable
fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(64.dp), // Slightly smaller
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = stringResource(R.string.save),
            tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline
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
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Idle
            ), onIntent = {})
    }
}

@Preview(name = "Recording State", showBackground = true)
@Composable
private fun AudioRecorderScreenRecordingPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Recording,
                durationMillis = 12300L,
                amplitudes = listOf(0.1f, 0.5f, 0.3f, 0.6f, 0.4f, 0.8f)
            ), onIntent = {})
    }
}

@Preview(name = "Recording Paused", showBackground = true)
@Composable
private fun AudioRecorderScreenRecordingPausedPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.RecordingPaused,
                durationMillis = 15000L,
                amplitudes = listOf(0.1f, 0.2f, 0.15f)
            ), onIntent = {})
    }
}

@Preview(name = "Playback Ready State", showBackground = true)
@Composable
private fun AudioRecorderScreenPlaybackReadyPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.PlaybackReady,
                durationMillis = 30000L,
                playbackProgressMillis = 0L,
                isSaveEnabled = true
            ), onIntent = {})
    }
}

@Preview(name = "Playing State", showBackground = true)
@Composable
private fun AudioRecorderScreenPlayingPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.Playing,
                durationMillis = 30000L,
                playbackProgressMillis = 10500L,
                isSaveEnabled = true
            ), onIntent = {})
    }
}

@Preview(name = "Playback Paused", showBackground = true)
@Composable
private fun AudioRecorderScreenPlaybackPausedPreview() {
    AnkiDroidTheme {
        AudioRecorderContent(
            uiState = AudioRecorderViewModel.UiState(
                state = AudioRecorderViewModel.RecordingState.PlaybackPaused,
                durationMillis = 30000L,
                playbackProgressMillis = 10500L,
                isSaveEnabled = true
            ), onIntent = {})
    }
}

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
