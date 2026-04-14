package com.ichi2.anki.multimedia.audio

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.multimediacard.AudioRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class AudioRecorderViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = com.ichi2.anki.ioDispatcher
) : AndroidViewModel(application) {

    sealed interface Intent {
        object StartRecording : Intent
        object PauseRecording : Intent
        object ResumeRecording : Intent
        object StopRecording : Intent
        object StartPlayback : Intent
        object PausePlayback : Intent
        object DiscardRecording : Intent
        object SaveRecording : Intent
    }

    sealed interface RecordingState {
        object Idle : RecordingState
        object Recording : RecordingState
        object RecordingPaused : RecordingState
        object PlaybackReady : RecordingState
        object Playing : RecordingState
        object PlaybackPaused : RecordingState
    }

    data class UiState(
        val state: RecordingState = RecordingState.Idle,
        val durationMillis: Long = 0L,
        val playbackProgressMillis: Long = 0L,
        val amplitude: Float = 0f,
        val amplitudes: List<Float> = emptyList(),
        val isSaveEnabled: Boolean = false,
        val savedFile: File? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var audioRecorder: AudioRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var playbackProgressJob: Job? = null

    private var startTimeMillis: Long = 0L
    private var accumulatedDurationMillis: Long = 0L

    fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.StartRecording -> startRecording()
            is Intent.PauseRecording -> pauseRecording()
            is Intent.ResumeRecording -> resumeRecording()
            is Intent.StopRecording -> stopRecording()
            is Intent.StartPlayback -> startPlayback()
            is Intent.PausePlayback -> pausePlayback()
            is Intent.DiscardRecording -> discardRecording()
            is Intent.SaveRecording -> saveRecording()
        }
    }

    private fun startRecording() {
        val context = getApplication<Application>()
        Timber.i("AudioRecorderViewModel: starting recording")
        viewModelScope.launch(ioDispatcher) {
            var tempFile: File? = null
            var localRecorder: AudioRecorder? = null
            try {
                tempFile = AudioRecordingController.generateTempAudioFile(context) ?: return@launch
                localRecorder = AudioRecorder()
                localRecorder.startRecording(context, tempFile)

                withContext(Dispatchers.Main) {
                    audioRecorder?.release()
                    audioRecorder = localRecorder
                    audioFile = tempFile

                    accumulatedDurationMillis = 0L
                    startTimeMillis = System.currentTimeMillis()

                    _uiState.update {
                        it.copy(
                            state = RecordingState.Recording,
                            durationMillis = 0L,
                            savedFile = null,
                            isSaveEnabled = true,
                            amplitudes = emptyList()
                        )
                    }

                    startTimer()
                    startAmplitudeMonitoring()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start recording")
                localRecorder?.release()
                tempFile?.delete()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(state = RecordingState.Idle) }
                }
            }
        }
    }

    private fun pauseRecording() {
        if (_uiState.value.state != RecordingState.Recording) return
        Timber.i("AudioRecorderViewModel: pausing recording")
        audioRecorder?.pause()

        timerJob?.cancel()
        amplitudeJob?.cancel()
        accumulatedDurationMillis += System.currentTimeMillis() - startTimeMillis

        _uiState.update { it.copy(state = RecordingState.RecordingPaused, amplitude = 0f) }
    }

    private fun resumeRecording() {
        if (_uiState.value.state != RecordingState.RecordingPaused) return
        Timber.i("AudioRecorderViewModel: resuming recording")
        audioRecorder?.resume()

        startTimeMillis = System.currentTimeMillis()

        _uiState.update { it.copy(state = RecordingState.Recording) }

        startTimer()
        startAmplitudeMonitoring()
    }

    private fun stopRecording() {
        if (_uiState.value.state !in listOf(
                RecordingState.Recording,
                RecordingState.RecordingPaused
            )
        ) return
        Timber.i("AudioRecorderViewModel: stopping recording")

        timerJob?.cancel()
        amplitudeJob?.cancel()

        if (_uiState.value.state == RecordingState.Recording) {
            accumulatedDurationMillis += System.currentTimeMillis() - startTimeMillis
        }

        try {
            audioRecorder?.stopRecording()
            audioRecorder?.release()
            audioRecorder = null

            _uiState.update {
                it.copy(
                    state = RecordingState.PlaybackReady,
                    amplitude = 0f,
                    durationMillis = accumulatedDurationMillis,
                    isSaveEnabled = true
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop recording")
            _uiState.update { it.copy(state = RecordingState.Idle) }
        }
    }

    private fun startPlayback() {
        val file = audioFile ?: return
        if (_uiState.value.state !in listOf(
                RecordingState.PlaybackReady,
                RecordingState.PlaybackPaused
            )
        ) return

        Timber.i("AudioRecorderViewModel: starting playback")
        viewModelScope.launch(ioDispatcher) {
            try {
                if (mediaPlayer == null) {
                    val localMediaPlayer = MediaPlayer()
                    try {
                        localMediaPlayer.setDataSource(file.absolutePath)
                        localMediaPlayer.prepare()

                        withContext(Dispatchers.Main) {
                            localMediaPlayer.setOnCompletionListener {
                                _uiState.update {
                                    it.copy(
                                        state = RecordingState.PlaybackReady,
                                        playbackProgressMillis = 0L
                                    )
                                }
                                playbackProgressJob?.cancel()
                            }
                            mediaPlayer = localMediaPlayer
                        }
                    } catch (e: Exception) {
                        localMediaPlayer.release()
                        throw e
                    }
                }

                withContext(Dispatchers.Main) {
                    mediaPlayer?.start()

                    _uiState.update { it.copy(state = RecordingState.Playing) }
                    startPlaybackProgressMonitoring()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start playback")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(state = RecordingState.PlaybackReady) }
                }
            }
        }
    }

    private fun pausePlayback() {
        if (_uiState.value.state != RecordingState.Playing) return
        Timber.i("AudioRecorderViewModel: pausing playback")

        playbackProgressJob?.cancel()
        mediaPlayer?.pause()

        _uiState.update { it.copy(state = RecordingState.PlaybackPaused) }
    }

    private fun discardRecording() {
        Timber.i("AudioRecorderViewModel: discarding recording")
        stopAndReset()
        audioFile?.delete()
        audioFile = null
    }

    private fun saveRecording() {
        if (_uiState.value.state in listOf(
                RecordingState.Recording,
                RecordingState.RecordingPaused
            )
        ) {
            stopRecording()
        }
        // Just marks that we want to save, fragment handles the result logic via uiState observation
        _uiState.update { it.copy(savedFile = audioFile) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.state == RecordingState.Recording) {
                val currentDuration =
                    accumulatedDurationMillis + (System.currentTimeMillis() - startTimeMillis)
                _uiState.update { it.copy(durationMillis = currentDuration) }
                delay(50) // Update relatively frequently for UI responsiveness
            }
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive && _uiState.value.state == RecordingState.Recording) {
                val amp = audioRecorder?.maxAmplitude() ?: 0
                val normalizedAmplitude = WaveformUtils.normalize(amp)
                _uiState.update { state ->
                    state.copy(
                        amplitude = normalizedAmplitude,
                        amplitudes = (state.amplitudes + normalizedAmplitude).takeLast(
                            MAX_AMPLITUDES
                        )
                    )
                }
                delay(50)
            }
        }
    }

    private fun startPlaybackProgressMonitoring() {
        playbackProgressJob?.cancel()
        playbackProgressJob = viewModelScope.launch {
            while (isActive && _uiState.value.state == RecordingState.Playing) {
                val player = mediaPlayer ?: break
                _uiState.update { it.copy(playbackProgressMillis = player.currentPosition.toLong()) }
                delay(50)
            }
        }
    }

    /**
     * Stops any active recording or playback and releases hardware resources.
     * This is intended to be called when the UI is backgrounded to ensure the
     * microphone or media player are not held indefinitely.
     */
    fun stopAllAndRelease() {
        Timber.i("AudioRecorderViewModel: stopAllAndRelease")
        if (_uiState.value.state in listOf(
                RecordingState.Recording,
                RecordingState.RecordingPaused
            )
        ) {
            stopRecording()
        }
        if (_uiState.value.state == RecordingState.Playing) {
            pausePlayback()
        }

        // Ensure media player is fully released if it was paused or playing
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun stopAndReset() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        playbackProgressJob?.cancel()

        audioRecorder?.release()
        audioRecorder = null

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        _uiState.update { UiState() }
    }

    override fun onCleared() {
        super.onCleared()
        stopAndReset()
    }

    companion object {
        private const val MAX_AMPLITUDES = 200
    }
}
