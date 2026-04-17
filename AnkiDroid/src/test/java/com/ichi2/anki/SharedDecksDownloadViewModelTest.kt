/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>                                                         *
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

package com.ichi2.anki

import android.app.DownloadManager
import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.ui.compose.shareddecks.DownloadIntent
import com.ichi2.anki.ui.compose.shareddecks.DownloadStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadViewModelTest : RobolectricTest() {

    private val downloadManager: DownloadManager = mockk()

    @Test
    fun `test initial state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            val first = awaitItem()
            assertEquals(DownloadStatus.Idle, first.status)
            assertEquals(0f, first.progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test setFileName updates state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.setFileName("test.apkg")
            val item = awaitItem()
            assertEquals("test.apkg", item.fileName)
            assertEquals(DownloadStatus.Downloading, item.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test onDownloadComplete updates state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.onDownloadComplete()
            val item = awaitItem()
            assertEquals(DownloadStatus.Complete, item.status)
            assertEquals(100f, item.progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test onDownloadFailed updates state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.onDownloadFailed()
            assertEquals(DownloadStatus.Failed, awaitItem().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test show and dismiss cancel dialog`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            assertFalse(awaitItem().showCancelDialog)
            viewModel.showCancelDialog()
            assertTrue(awaitItem().showCancelDialog)
            viewModel.dismissCancelDialog()
            assertFalse(awaitItem().showCancelDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test polling updates progress`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SharedDecksDownloadViewModel(testDispatcher)

        every { downloadManager.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON
                )
            ).apply {
                addRow(arrayOf<Any>(50L, 100L, DownloadManager.STATUS_RUNNING, 0))
            }
        }

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.startPolling(downloadManager, 123L)

            val downloadIdState = awaitItem()
            assertEquals(123L, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Idle, downloadIdState.status)

            advanceTimeBy(100)
            val item = awaitItem()
            assertEquals(50f, item.progress)
            assertEquals(DownloadStatus.Downloading, item.status)
            viewModel.stopPolling()

            advanceTimeBy(2000)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test polling handles waiting for network`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SharedDecksDownloadViewModel(testDispatcher)

        every { downloadManager.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON
                )
            ).apply {
                addRow(
                    arrayOf<Any>(
                        0L,
                        100L,
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK
                    )
                )
            }
        }

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.startPolling(downloadManager, 123L)

            val downloadIdState = awaitItem()
            assertEquals(123L, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Idle, downloadIdState.status)

            advanceTimeBy(100)
            val item = awaitItem()
            assertEquals(DownloadStatus.WaitingForNetwork, item.status)
            viewModel.stopPolling()

            advanceTimeBy(2000)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test stopPolling cancels an immediate polling start`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SharedDecksDownloadViewModel(testDispatcher)

        every { downloadManager.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON
                )
            ).apply {
                addRow(arrayOf<Any>(50L, 100L, DownloadManager.STATUS_RUNNING, 0))
            }
        }

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)

            viewModel.startPolling(downloadManager, 123L)
            viewModel.stopPolling()

            val downloadIdState = awaitItem()
            assertEquals(123L, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Idle, downloadIdState.status)

            advanceTimeBy(1100)

            expectNoEvents()
            verify(exactly = 0) { downloadManager.query(any()) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test ConfirmCancel with null downloadManager resets state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            awaitItem() // Initial (Idle)

            // Drive to non-Idle state
            viewModel.setFileName("test.apkg")
            assertEquals(DownloadStatus.Downloading, awaitItem().status)

            viewModel.showCancelDialog()
            assertTrue(awaitItem().showCancelDialog)

            viewModel.onIntent(DownloadIntent.ConfirmCancel, null)

            val item = awaitItem()
            assertFalse(item.showCancelDialog)
            assertEquals(DownloadStatus.Idle, item.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test RetryClicked updates state and removes download`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        val downloadId = 123L

        every { downloadManager.remove(downloadId) } returns 1

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)
            viewModel.setDownloadId(downloadId)
            viewModel.onDownloadFailed()

            val downloadIdState = awaitItem()
            assertEquals(downloadId, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Idle, downloadIdState.status)

            assertEquals(DownloadStatus.Failed, awaitItem().status)
            viewModel.onIntent(DownloadIntent.RetryClicked, downloadManager)

            val item = awaitItem()
            assertEquals(DownloadStatus.Downloading, item.status)
            assertEquals(0f, item.progress)

            verify { downloadManager.remove(downloadId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test RetryClicked with null downloadManager still updates state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        val downloadId = 123L

        viewModel.setDownloadId(downloadId)
        viewModel.onDownloadFailed()

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Failed, awaitItem().status)
            viewModel.onIntent(DownloadIntent.RetryClicked, null)

            val item = awaitItem()
            assertEquals(DownloadStatus.Downloading, item.status)
            assertEquals(0f, item.progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test OpenInBrowserClicked resets state and removes download`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        val downloadId = 123L
        viewModel.setDownloadId(downloadId)
        viewModel.onDownloadComplete()

        every { downloadManager.remove(downloadId) } returns 1

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Complete, awaitItem().status)
            viewModel.onIntent(DownloadIntent.OpenInBrowserClicked, downloadManager)

            val item = awaitItem()
            assertEquals(DownloadStatus.Idle, item.status)

            verify { downloadManager.remove(downloadId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test OpenInBrowserClicked with null downloadManager still resets state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        val downloadId = 123L
        viewModel.setDownloadId(downloadId)
        viewModel.onDownloadComplete()

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Complete, awaitItem().status)
            viewModel.onIntent(DownloadIntent.OpenInBrowserClicked, null)

            val item = awaitItem()
            assertEquals(DownloadStatus.Idle, item.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test ConfirmCancel with downloadManager removes download and resets state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        val downloadId = 123L
        viewModel.setDownloadId(downloadId)
        viewModel.setFileName("test.apkg")

        every { downloadManager.remove(downloadId) } returns 1

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Downloading, awaitItem().status)
            viewModel.onIntent(DownloadIntent.ConfirmCancel, downloadManager)

            val item = awaitItem()
            assertEquals(DownloadStatus.Idle, item.status)

            verify { downloadManager.remove(downloadId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test polling does not overwrite terminal states`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SharedDecksDownloadViewModel(testDispatcher)

        // Mock DownloadManager to return a running status
        every { downloadManager.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON
                )
            ).apply {
                addRow(arrayOf<Any>(80L, 100L, DownloadManager.STATUS_RUNNING, 0))
            }
        }

        viewModel.uiState.test {
            assertEquals(DownloadStatus.Idle, awaitItem().status)

            // Start downloading state
            viewModel.setFileName("test.apkg")
            assertEquals(DownloadStatus.Downloading, awaitItem().status)

            // Start polling
            viewModel.startPolling(downloadManager, 123L)

            val downloadIdState = awaitItem()
            assertEquals(123L, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Downloading, downloadIdState.status)

            // Advance small amount to trigger first query, but we'll race it with onDownloadComplete
            // Actually, with StandardTestDispatcher, the polling coroutine won't run until we yield or advance.

            // Set terminal state BEFORE the polling loop has a chance to update (simulating race)
            viewModel.onDownloadComplete()
            assertEquals(DownloadStatus.Complete, awaitItem().status)

            // Now let the polling loop execute checkDownloadProgress
            advanceTimeBy(1100)

            expectNoEvents()
            assertEquals(DownloadStatus.Complete, viewModel.uiState.value.status)
            assertEquals(100f, viewModel.uiState.value.progress)

            viewModel.stopPolling()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test polling does not overwrite idle state after reset`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = SharedDecksDownloadViewModel(testDispatcher)

        every { downloadManager.query(any()) } answers {
            MatrixCursor(
                arrayOf(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                    DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_REASON
                )
            ).apply {
                addRow(arrayOf<Any>(50L, 100L, DownloadManager.STATUS_RUNNING, 0))
            }
        }

        viewModel.uiState.test {
            awaitItem() // Idle
            viewModel.setFileName("test.apkg")
            awaitItem() // Downloading

            viewModel.startPolling(downloadManager, 123L)

            val downloadIdState = awaitItem()
            assertEquals(123L, downloadIdState.downloadId)
            assertEquals(DownloadStatus.Downloading, downloadIdState.status)

            // Reset state while polling might be active
            viewModel.resetState()
            assertEquals(DownloadStatus.Idle, awaitItem().status)

            // Let polling loop run
            advanceTimeBy(1100)

            // Should remain Idle
            expectNoEvents()
            assertEquals(DownloadStatus.Idle, viewModel.uiState.value.status)
            assertEquals(0f, viewModel.uiState.value.progress)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
