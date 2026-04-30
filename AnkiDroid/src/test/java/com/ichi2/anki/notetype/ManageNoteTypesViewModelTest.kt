/****************************************************************************************
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                       *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                                 *
 * See the GNU General Public License for more details.                                 *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.notetype

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Collection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import net.ankiweb.rsdroid.Backend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ManageNoteTypesViewModelTest : RobolectricTest() {

    @Test
    fun `refresh failure emits ui error event and clears loading`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val originalIoDispatcher = ioDispatcher
        val expectedMessage = "refresh failed"
        val mockBackend = mockk<Backend> {
            every { getNotetypeNamesAndCounts() } throws IllegalStateException("refresh failed")
        }
        val mockCollection = mockk<Collection> {
            every { backend } returns mockBackend
            every { dbClosed } returns false
        }

        ioDispatcher = testDispatcher
        CollectionManager.setColForTests(mockCollection)

        try {
            val viewModel = ManageNoteTypesViewModel()

            viewModel.uiEvents.test {
                advanceUntilIdle()

                assertEquals(
                    ManageNoteTypesUiEvent.ShowErrorMessage(expectedMessage),
                    awaitItem(),
                )
                assertFalse(viewModel.uiState.value.isLoading)

                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            CollectionManager.setColForTests(null)
            ioDispatcher = originalIoDispatcher
        }
    }

    @Test
    fun `refresh success updates loading state correctly`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val originalIoDispatcher = ioDispatcher
        val mockBackend = mockk<Backend> {
            every { getNotetypeNamesAndCounts() } returns emptyList()
            every { getNotetypeNames() } returns emptyList()
            every { getStockNotetypeLegacy(any()) } returns com.google.protobuf.ByteString.copyFromUtf8("{\"name\": \"MockNotetype\"}")
        }
        val mockCollection = mockk<Collection> {
            every { backend } returns mockBackend
            every { dbClosed } returns false
        }

        ioDispatcher = testDispatcher
        CollectionManager.setColForTests(mockCollection)

        try {
            val viewModel = ManageNoteTypesViewModel()

            assertFalse(viewModel.uiState.value.isLoading)

            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)

            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
        } finally {
            CollectionManager.setColForTests(null)
            ioDispatcher = originalIoDispatcher
        }
    }
}
