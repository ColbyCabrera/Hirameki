/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.multimedia.audio

import android.app.Application
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
