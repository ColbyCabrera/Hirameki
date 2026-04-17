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
    fun `test ConfirmCancel with null downloadManager resets state`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.uiState.test {
            awaitItem() // Initial
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
        viewModel.setDownloadId(downloadId)
        viewModel.onDownloadFailed()

        every { downloadManager.remove(downloadId) } returns 1

        viewModel.uiState.test {
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
}
