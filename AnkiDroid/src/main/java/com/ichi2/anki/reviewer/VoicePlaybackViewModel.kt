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
package com.ichi2.anki.reviewer

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.anki.multimediacard.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * ViewModel for Voice Playback (mic toolbar) in the Reviewer.
 * Manages recording and playback of audio for self-testing purposes.
 */
class VoicePlaybackViewModel : ViewModel() {

    sealed interface RecordingState {
        data object Idle : RecordingState
        data object Recording : RecordingState
        data object PlaybackReady : RecordingState
        data class Playing(val progress: Float) : RecordingState
    }

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var audioRecorder: AudioRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var amplitudeJob: Job? = null
    private var playbackProgressJob: Job? = null

    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
        if (!visible) {
            stopAndReset()
        }
    }

    fun toggleRecording(context: Context) {
        when (_state.value) {
            is RecordingState.Idle -> {
                val tempFile = AudioRecordingController.generateTempAudioFile(context)
                if (tempFile != null) startRecording(context, tempFile)
            }

            is RecordingState.Recording -> stopRecording()
            is RecordingState.PlaybackReady -> {
                val tempFile = AudioRecordingController.generateTempAudioFile(context)
                if (tempFile != null) startRecording(context, tempFile)
            }

            is RecordingState.Playing -> {
                stopPlayback()
                val tempFile = AudioRecordingController.generateTempAudioFile(context)
                if (tempFile != null) startRecording(context, tempFile)
            }
        }
    }

    private fun startRecording(context: Context, tempAudioFile: File) {
        Timber.i("VoicePlaybackViewModel: starting recording")
        try {
            audioRecorder?.release()
            audioRecorder = AudioRecorder().apply {
                startRecording(context, tempAudioFile)
            }
            audioFile = tempAudioFile
            _state.value = RecordingState.Recording
            startAmplitudeMonitoring()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            _state.value = RecordingState.Idle
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive && _state.value == RecordingState.Recording) {
                val amp = audioRecorder?.maxAmplitude() ?: 0
                // Normalize amplitude (max is typically around 32767)
                _amplitude.value = (amp / 32767f).coerceIn(0f, 1f)
                delay(50)
            }
        }
    }

    private fun stopRecording() {
        Timber.i("VoicePlaybackViewModel: stopping recording")
        amplitudeJob?.cancel()
        try {
            audioRecorder?.stopRecording()
            audioRecorder?.release()
            audioRecorder = null
            _state.value = RecordingState.PlaybackReady
            _amplitude.value = 0f
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop recording")
            _state.value = RecordingState.Idle
        }
    }

    fun togglePlayback() {
        when (val currentState = _state.value) {
            is RecordingState.PlaybackReady -> startPlayback()
            is RecordingState.Playing -> stopPlayback()
            else -> {}
        }
    }

    private fun startPlayback() {
        val file = audioFile ?: return
        Timber.i("VoicePlaybackViewModel: starting playback")
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _state.value = RecordingState.PlaybackReady
                    playbackProgressJob?.cancel()
                }
            }
            _state.value = RecordingState.Playing(0f)
            startPlaybackProgressMonitoring()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start playback")
            _state.value = RecordingState.PlaybackReady
        }
    }

    private fun startPlaybackProgressMonitoring() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (isActive && _state.value is RecordingState.Playing) {
                val player = mediaPlayer ?: break
                val progress = if (player.duration > 0) {
                    player.currentPosition.toFloat() / player.duration
                } else 0f
                _state.value = RecordingState.Playing(progress)
                delay(50)
            }
        }
    }

    private fun stopPlayback() {
        Timber.i("VoicePlaybackViewModel: stopping playback")
        playbackProgressJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _state.value = RecordingState.PlaybackReady
    }

    /** Returns true if there is a saved recording available for playback */
    val hasRecording: Boolean
        get() = audioFile?.exists() == true && _state.value is RecordingState.PlaybackReady

    /** For JS API: Start recording if idle, otherwise no-op */
    fun startRecordingIfIdle(context: Context) {
        if (_state.value == RecordingState.Idle) {
            val tempFile = AudioRecordingController.generateTempAudioFile(context)
            if (tempFile != null) startRecording(context, tempFile)
        }
    }

    /** For JS API: Stop recording and prepare for playback */
    fun stopAndSaveRecording() {
        if (_state.value == RecordingState.Recording) {
            stopRecording()
        }
    }

    /** For JS API: Play the saved recording if available */
    fun playRecording() {
        if (_state.value == RecordingState.PlaybackReady) {
            startPlayback()
        }
    }

    fun discardRecording() {
        Timber.i("VoicePlaybackViewModel: discarding recording")
        stopAndReset()
        audioFile?.delete()
        audioFile = null
    }

    private fun stopAndReset() {
        amplitudeJob?.cancel()
        playbackProgressJob?.cancel()
        audioRecorder?.release()
        audioRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
        _state.value = RecordingState.Idle
        _amplitude.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopAndReset()
    }
}
