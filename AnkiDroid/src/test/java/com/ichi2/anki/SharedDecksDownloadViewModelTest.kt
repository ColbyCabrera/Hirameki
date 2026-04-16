package com.ichi2.anki

import android.app.DownloadManager
import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ui.compose.shareddecks.DownloadStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadViewModelTest : RobolectricTest() {

    private val downloadManager: DownloadManager = mockk()

    @Test
    fun `test initial state`() {
        val viewModel = SharedDecksDownloadViewModel()
        assertEquals(DownloadStatus.Idle, viewModel.uiState.value.status)
        assertEquals(0f, viewModel.uiState.value.progress)
        assertEquals("0%", viewModel.uiState.value.progressText)
    }

    @Test
    fun `test setFileName updates state`() {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.setFileName("test.apkg")
        assertEquals("test.apkg", viewModel.uiState.value.fileName)
        assertEquals(DownloadStatus.Downloading, viewModel.uiState.value.status)
    }

    @Test
    fun `test onDownloadComplete updates state`() {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.onDownloadComplete("100%")
        assertEquals(DownloadStatus.Complete, viewModel.uiState.value.status)
        assertEquals(100f, viewModel.uiState.value.progress)
        assertEquals("100%", viewModel.uiState.value.progressText)
    }

    @Test
    fun `test onDownloadFailed updates state`() {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.onDownloadFailed()
        assertEquals(DownloadStatus.Failed, viewModel.uiState.value.status)
    }

    @Test
    fun `test show and dismiss cancel dialog`() {
        val viewModel = SharedDecksDownloadViewModel()
        viewModel.showCancelDialog()
        assertTrue(viewModel.uiState.value.showCancelDialog)
        viewModel.dismissCancelDialog()
        assertTrue(!viewModel.uiState.value.showCancelDialog)
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

        try {
            viewModel.startPolling(downloadManager, 123L) { "%.0f%%".format(it) }

            // Wait for first poll
            advanceTimeBy(100)

            assertEquals(50f, viewModel.uiState.value.progress)
            assertEquals("50%", viewModel.uiState.value.progressText)
            assertEquals(DownloadStatus.Downloading, viewModel.uiState.value.status)
            viewModel.stopPolling()
        } finally {
            cursor.close()
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

        try {
            viewModel.startPolling(downloadManager, 123L) { "Waiting..." }

            advanceTimeBy(100)

            assertEquals(DownloadStatus.WaitingForNetwork, viewModel.uiState.value.status)
            viewModel.stopPolling()
        } finally {
            cursor.close()
        }
    }
}
