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

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Downloading : DownloadStatus
    data object WaitingForNetwork : DownloadStatus
    data object Failed : DownloadStatus
    data object Complete : DownloadStatus
}

sealed interface DownloadIntent {
    data object CancelClicked : DownloadIntent
    data object ConfirmCancel : DownloadIntent
    data object DismissCancelDialog : DownloadIntent
    object RetryClicked : DownloadIntent
    object ImportClicked : DownloadIntent
    object OpenInBrowserClicked : DownloadIntent
}

data class DownloadUiState(
    val fileName: String = "",
    val progress: Float = 0f,
    val progressText: String = "0%",
    val status: DownloadStatus = DownloadStatus.Idle,
    val showCancelDialog: Boolean = false
)
