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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SharedDecksDownloadViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    var downloadId: Long = 0
        private set

    private var progressJob: Job? = null

    internal var dispatcher = Dispatchers.IO

    fun onIntent(intent: DownloadIntent, downloadManager: DownloadManager? = null) {
        when (intent) {
            DownloadIntent.CancelClicked -> showCancelDialog()
            DownloadIntent.ConfirmCancel -> {
                downloadManager?.let { cancelDownload(it, downloadId) }
            }

            DownloadIntent.DismissCancelDialog -> dismissCancelDialog()
            DownloadIntent.RetryClicked -> {
                _uiState.update { it.copy(status = DownloadStatus.Downloading, progress = 0f) }
            }

            DownloadIntent.ImportClicked -> {
                // Handled in Fragment for side effects
            }

            DownloadIntent.OpenInBrowserClicked -> {
                resetState()
            }
        }
    }

    fun startPolling(
        downloadManager: DownloadManager,
        downloadId: Long,
        progressTextProvider: (Float) -> String
    ) {
        this.downloadId = downloadId
        progressJob?.cancel()
        progressJob = viewModelScope.launch(dispatcher) {
            while (true) {
                checkDownloadProgress(downloadManager, downloadId, progressTextProvider)
                delay(1000)
            }
        }
    }

    fun stopPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun checkDownloadProgress(
        downloadManager: DownloadManager, downloadId: Long, progressTextProvider: (Float) -> String
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = try {
            downloadManager.query(query)
        } catch (_: Exception) {
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
                    progressText = progressTextProvider(downloadProgress),
                    status = if (isWaitingForNetwork) DownloadStatus.WaitingForNetwork else DownloadStatus.Downloading
                )
            }
        }
    }

    fun onDownloadComplete(progressText: String) {
        stopPolling()
        _uiState.update {
            it.copy(
                progress = 100f, progressText = progressText, status = DownloadStatus.Complete
            )
        }
    }

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

    fun resetState() {
        stopPolling()
        _uiState.value = DownloadUiState()
    }

    fun cancelDownload(downloadManager: DownloadManager, downloadId: Long) {
        _uiState.update { it.copy(showCancelDialog = false) }
        downloadManager.remove(downloadId)
        resetState()
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
