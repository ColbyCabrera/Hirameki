package com.ichi2.anki.multimedia.audio

import android.app.Application
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecorderViewModelTest {

    private lateinit var viewModel: AudioRecorderViewModel
    private val application: Application = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AudioRecorderViewModel(application)
    }

    @Test
    fun `initial state has empty amplitudes`() {
        assertEquals(emptyList<Float>(), viewModel.uiState.value.amplitudes)
    }

    // Since startAmplitudeMonitoring is internal and uses Coroutines/delay, 
    // it's hard to test without refactoring how audioRecorder is injected.
    // However, I can verify the MAX_AMPLITUDES logic if I could trigger the monitoring.
    // For now, this serves as a baseline check that the new field exists and is initialized.
}
