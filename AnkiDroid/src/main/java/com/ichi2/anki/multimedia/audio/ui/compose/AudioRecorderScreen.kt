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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.audio.AudioRecorderViewModel
import com.ichi2.anki.multimedia.audio.AudioWaveformCompose

@Composable
fun AudioRecorderScreen(viewModel: AudioRecorderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // Dark background to match image
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Delete / Top Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (uiState.state != AudioRecorderViewModel.RecordingState.Idle) {
                    IconButton(onClick = { viewModel.processIntent(AudioRecorderViewModel.Intent.DiscardRecording) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_note_message),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (uiState.state in listOf(
                    AudioRecorderViewModel.RecordingState.Recording,
                    AudioRecorderViewModel.RecordingState.RecordingPaused
                )) {
                AudioWaveformCompose(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    amplitude = uiState.amplitude,
                    isRecording = uiState.state == AudioRecorderViewModel.RecordingState.Recording
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Text / Time
            Text(
                text = formatDuration(uiState.durationMillis.takeIf { uiState.state in listOf(AudioRecorderViewModel.RecordingState.Recording, AudioRecorderViewModel.RecordingState.RecordingPaused) } ?: uiState.playbackProgressMillis),
                fontSize = 48.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.state == AudioRecorderViewModel.RecordingState.Idle) {
                    Spacer(modifier = Modifier.weight(1f))
                    RecordButton(
                        onClick = { viewModel.processIntent(AudioRecorderViewModel.Intent.StartRecording(context)) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SaveButton(
                        enabled = false,
                        onClick = { }
                    )
                } else if (uiState.state in listOf(
                        AudioRecorderViewModel.RecordingState.Recording,
                        AudioRecorderViewModel.RecordingState.RecordingPaused
                    )
                ) {
                    PauseResumeButton(
                        isPaused = uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused,
                        onClick = {
                            if (uiState.state == AudioRecorderViewModel.RecordingState.RecordingPaused) {
                                viewModel.processIntent(AudioRecorderViewModel.Intent.ResumeRecording)
                            } else {
                                viewModel.processIntent(AudioRecorderViewModel.Intent.PauseRecording)
                            }
                        }
                    )
                    StopButton(
                        onClick = { viewModel.processIntent(AudioRecorderViewModel.Intent.StopRecording) }
                    )
                    SaveButton(
                        enabled = false,
                        onClick = { }
                    )
                } else if (uiState.state in listOf(
                        AudioRecorderViewModel.RecordingState.PlaybackReady,
                        AudioRecorderViewModel.RecordingState.Playing,
                        AudioRecorderViewModel.RecordingState.PlaybackPaused
                    )
                ) {
                    PlayPauseButton(
                        isPlaying = uiState.state == AudioRecorderViewModel.RecordingState.Playing,
                        onClick = {
                            if (uiState.state == AudioRecorderViewModel.RecordingState.Playing) {
                                viewModel.processIntent(AudioRecorderViewModel.Intent.PausePlayback)
                            } else {
                                viewModel.processIntent(AudioRecorderViewModel.Intent.StartPlayback)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SaveButton(
                        enabled = uiState.isSaveEnabled,
                        onClick = { viewModel.processIntent(AudioRecorderViewModel.Intent.SaveRecording) }
                    )
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB4AB))
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_record),
            contentDescription = stringResource(R.string.record_voice),
            tint = Color(0xFF690005)
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB4AB))
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_stop), // Need stop icon
            contentDescription = stringResource(R.string.stop_recording),
            tint = Color(0xFF690005)
        )
        Text(
            text = stringResource(R.string.stop_recording),
            color = Color(0xFF690005),
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4444))
    ) {
        Icon(
            painter = painterResource(id = if (isPaused) R.drawable.round_play_arrow_24 else R.drawable.round_pause_24), // Ensure icons
            contentDescription = if (isPaused) stringResource(R.string.play_recording) else stringResource(R.string.pause_playback),
            tint = Color.White
        )
        Text(
            text = if (isPaused) stringResource(R.string.play_recording) else stringResource(R.string.pause_playback),
            color = Color.White,
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
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4444))
    ) {
        Icon(
            painter = painterResource(id = if (isPlaying) R.drawable.round_pause_24 else R.drawable.round_play_arrow_24),
            contentDescription = if (isPlaying) stringResource(R.string.pause_playback) else stringResource(R.string.play_recording),
            tint = Color.White
        )
        Text(
            text = if (isPlaying) stringResource(R.string.pause_playback) else stringResource(R.string.play_recording),
            color = Color.White,
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
            containerColor = Color(0xFF4A4444),
            disabledContainerColor = Color(0xFF2C2C2C)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = stringResource(R.string.save),
            tint = if (enabled) Color.White else Color.Gray
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val deciseconds = (millis % 1000) / 100
    return String.format("%02d:%02d.%d", minutes, seconds, deciseconds)
}
