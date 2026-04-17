package com.ichi2.anki

import android.app.DownloadManager
import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.ui.compose.shareddecks.DownloadStatus
import io.mockk.every
import io.mockk.mockk
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
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.dispatcher = StandardTestDispatcher(testScheduler)
        val cursor = MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON
            )
        )
        cursor.addRow(arrayOf<Any>(50L, 100L, DownloadManager.STATUS_RUNNING, 0))

        every { downloadManager.query(any()) } returns cursor

        cursor.use {
            viewModel.uiState.test {
                assertEquals(DownloadStatus.Idle, awaitItem().status)
                viewModel.startPolling(downloadManager, 123L)

                // Wait for first poll
                advanceTimeBy(100)

                val item = awaitItem()
                assertEquals(50f, item.progress)
                assertEquals(DownloadStatus.Downloading, item.status)
                viewModel.stopPolling()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `test polling handles waiting for network`() = runTest {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.dispatcher = StandardTestDispatcher(testScheduler)
        val cursor = MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON
            )
        )
        cursor.addRow(
            arrayOf<Any>(
                0L, 100L, DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK
            )
        )

        every { downloadManager.query(any()) } returns cursor

        cursor.use {
            viewModel.uiState.test {
                assertEquals(DownloadStatus.Idle, awaitItem().status)
                viewModel.startPolling(downloadManager, 123L)

                advanceTimeBy(100)

                assertEquals(DownloadStatus.WaitingForNetwork, awaitItem().status)
                viewModel.stopPolling()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
