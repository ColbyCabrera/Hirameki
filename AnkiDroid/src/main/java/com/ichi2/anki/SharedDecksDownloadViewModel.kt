/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2025 AnkiDroid                                                         *
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.ui.compose.shareddecks.DownloadIntent
import com.ichi2.anki.ui.compose.shareddecks.DownloadStatus
import com.ichi2.anki.ui.compose.shareddecks.DownloadUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * ViewModel for managing the state and logic of downloading shared decks.
 * This ViewModel handles polling the [android.app.DownloadManager] for progress,
 * processing UI intents, and maintaining the [DownloadUiState].
 */
class SharedDecksDownloadViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    var downloadId: Long = 0
        private set

    private var progressJob: Job? = null
    private val progressJobMutex = Mutex()

    /**
     * Processes a user [DownloadIntent] and updates the UI state accordingly.
     * Some intents may require the [DownloadManager] to perform actions like cancelling a download.
     *
     * @param intent The user action to process.
     * @param downloadManager Optional [DownloadManager] for executing download-related commands.
     */
    fun onIntent(intent: DownloadIntent, downloadManager: DownloadManager? = null) {

        when (intent) {
            DownloadIntent.CancelClicked -> showCancelDialog()
            DownloadIntent.ConfirmCancel -> {
                if (downloadManager != null) {
                    cancelDownload(downloadManager, downloadId)
                } else {
                    Timber.w(
                        "ConfirmCancel: downloadManager is null, cannot cancel download ID %d",
                        downloadId
                    )
                    resetState()
                }
            }

            DownloadIntent.DismissCancelDialog -> dismissCancelDialog()
            DownloadIntent.RetryClicked -> {
                downloadManager?.remove(downloadId)
                _uiState.update { it.copy(status = DownloadStatus.Downloading, progress = 0f) }
            }

            DownloadIntent.ImportClicked -> {
                // Handled in Fragment for side effects
            }

            DownloadIntent.OpenInBrowserClicked -> {
                downloadManager?.remove(downloadId)
                resetState()
            }
        }
    }

    fun setDownloadId(id: Long) {
        this.downloadId = id
    }

    /**
     * Starts a periodic job to poll the [DownloadManager] for the current progress of a download.
     *
     * @param downloadManager The system service used to query download status.
     * @param downloadId The unique ID of the download to track.
     */
    fun startPolling(
        downloadManager: DownloadManager, downloadId: Long
    ) {
        this.downloadId = downloadId
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            progressJobMutex.withLock {
                clearProgressJobLocked()
                progressJob = viewModelScope.launch(dispatcher) {
                    while (true) {
                        checkDownloadProgress(downloadManager, downloadId)
                        delay(1000)
                    }
                }
            }
        }
    }

    /**
     * Stops the periodic progress polling job if it is currently running.
     */
    fun stopPolling() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            progressJobMutex.withLock {
                clearProgressJobLocked()
            }
        }
    }

    private fun stopPollingBlocking() {
        runBlocking {
            progressJobMutex.withLock {
                clearProgressJobLocked()
            }
        }
    }

    private fun clearProgressJobLocked() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun checkDownloadProgress(
        downloadManager: DownloadManager, downloadId: Long
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = try {
            downloadManager.query(query)
        } catch (e: Exception) {
            Timber.e(e, "Failed to query DownloadManager for downloadId=%d", downloadId)
            null
        }

        cursor?.use {
            if (!it.moveToFirst()) return

            val downloadedBytesIdx =
                it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytesIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)

            if (downloadedBytesIdx == -1 || totalBytesIdx == -1 || statusIdx == -1 || reasonIdx == -1) return

            val downloadedBytes = it.getLong(downloadedBytesIdx)
            val totalBytes = it.getLong(totalBytesIdx)

            val downloadProgress: Float = if (totalBytes > 0L) {
                (downloadedBytes.toDouble() / totalBytes * 100).toFloat()
            } else {
                0f
            }

            val isWaitingForNetwork =
                it.getInt(statusIdx) == DownloadManager.STATUS_PAUSED && it.getInt(reasonIdx) == DownloadManager.PAUSED_WAITING_FOR_NETWORK

            _uiState.update { state ->
                state.copy(
                    progress = downloadProgress,
                    status = if (isWaitingForNetwork) DownloadStatus.WaitingForNetwork else DownloadStatus.Downloading
                )
            }
        }
    }

    /**
     * Updates the UI state to reflect a successfully completed download.
     */
    fun onDownloadComplete() {
        stopPolling()
        _uiState.update {
            it.copy(
                progress = 100f, status = DownloadStatus.Complete
            )
        }
    }

    /**
     * Updates the UI state to reflect a failed download attempt.
     */
    fun onDownloadFailed() {

        stopPolling()
        _uiState.update { it.copy(status = DownloadStatus.Failed) }
    }

    fun setFileName(fileName: String) {
        _uiState.update { it.copy(fileName = fileName, status = DownloadStatus.Downloading) }
    }

    fun showCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = true) }
    }

    fun dismissCancelDialog() {
        _uiState.update { it.copy(showCancelDialog = false) }
    }

    /**
     * Resets the ViewModel to its initial idle state.
     */
    fun resetState() {
        stopPolling()
        _uiState.value = DownloadUiState()
    }

    private fun cancelDownload(downloadManager: DownloadManager, downloadId: Long) {
        downloadManager.remove(downloadId)
        resetState()
    }

    override fun onCleared() {
        stopPollingBlocking()
        super.onCleared()
    }
}
