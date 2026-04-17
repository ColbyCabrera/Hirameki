/*
 *  Copyright (c) 2026 AnkiDroid
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
package com.ichi2.anki.ui.compose.shareddecks

/**
 * Represents the possible statuses of a shared deck download.
 */
sealed interface DownloadStatus {
    /** The download has not started yet. */
    data object Idle : DownloadStatus
    data object Downloading : DownloadStatus
    data object WaitingForNetwork : DownloadStatus
    data object Failed : DownloadStatus
    data object Complete : DownloadStatus
}

/**
 * Intents representing user actions on the shared decks download screen.
 */
sealed interface DownloadIntent {
    /** User clicked the cancel button to stop the download. */
    data object CancelClicked : DownloadIntent

    /** User confirmed the cancellation in the dialog. */
    data object ConfirmCancel : DownloadIntent

    /** User dismissed the cancellation dialog without cancelling. */
    data object DismissCancelDialog : DownloadIntent

    /** User clicked to retry a failed download. */
    data object RetryClicked : DownloadIntent

    /** User clicked to import the successfully downloaded deck. */
    data object ImportClicked : DownloadIntent

    /** User clicked to open the deck page in a web browser. */
    data object OpenInBrowserClicked : DownloadIntent
}

/**
 * UI state for the Shared Decks Download screen.
 *
 * @property downloadId The active DownloadManager ID for the current download, if any.
 * @property fileName The name of the file being downloaded.
 * @property progress The current download progress as a percentage (0-100).
 * @property status The current [DownloadStatus] of the operation.
 * @property showCancelDialog Whether the cancellation confirmation dialog should be visible.
 */
data class DownloadUiState(
    val downloadId: Long = 0L,
    val fileName: String = "",
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.Idle,
    val showCancelDialog: Boolean = false
)
