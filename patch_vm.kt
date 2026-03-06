/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.mediacheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.observability.undoableOp
import com.ichi2.async.deleteMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.ichi2.anki.R
import com.ichi2.anki.CollectionManager.TR
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.ichi2.anki.launchCatchingIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@NeedsTest("Test the media check process i.e. the buttons and views")
class MediaCheckViewModel : ViewModel() {

    sealed class ProgressState {
        data object Idle : ProgressState()
        data class ActiveRes(val messageRes: Int) : ProgressState()
    }

    sealed class UiEvent {
        data class ShowResultDialog(val titleRes: Int, val message: String) : UiEvent()
        data object ShowTrashRestoredDialog : UiEvent()
        data object ShowTrashDeletedDialog : UiEvent()
        data object ShowDeletionResult : UiEvent()
        data class ShowError(val message: String) : UiEvent()
    }

    private val _mediaCheckResult = MutableStateFlow<CheckMediaResponse?>(null)
    val mediaCheckResult: StateFlow<CheckMediaResponse?> = _mediaCheckResult

    private val deletedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val taggedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)

    private val _progressState = MutableStateFlow<ProgressState>(ProgressState.Idle)
    val progressState: StateFlow<ProgressState> = _progressState

    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val deletedFiles: Int
        get() = deletedFilesCount.value

    val taggedFiles: Int
        get() = taggedFilesCount.value

    private fun launchWithProgress(messageRes: Int, block: suspend CoroutineScope.() -> Unit): Job =
        launchCatchingIO(
            errorMessageHandler = { _uiEvent.send(UiEvent.ShowError(it)) }
        ) {
            _progressState.value = ProgressState.ActiveRes(messageRes)
            try {
                block()
            } finally {
                _progressState.value = ProgressState.Idle
            }
        }

    fun tagMissing(tag: String): Job = launchWithProgress(R.string.check_media_adding_missing_tag) {
        val taggedNotes = undoableOp {
            tags.bulkAdd(_mediaCheckResult.value?.missingMediaNotesList ?: listOf(), tag)
        }
        taggedFilesCount.value = taggedNotes.count
        _uiEvent.send(UiEvent.ShowResultDialog(R.string.check_media_tags_added, TR.browsingNotesUpdated(taggedFilesCount.value)))
    }

    fun checkMedia(): Job = launchWithProgress(R.string.check_media_message) {
        val result = withCol { media.check() }
        _mediaCheckResult.value = result
    }

    fun deleteTrash(): Job = launchWithProgress(R.string.dialog_processing) {
        withCol { media.emptyTrash() }
        _uiEvent.send(UiEvent.ShowTrashDeletedDialog)
    }

    fun restoreTrash(): Job = launchWithProgress(R.string.dialog_processing) {
        withCol { media.restoreTrash() }
        _uiEvent.send(UiEvent.ShowTrashRestoredDialog)
    }

    fun deleteUnusedMedia(): Job = launchWithProgress(R.string.delete_media_message) {
        val deletedMedia = withCol { deleteMedia(this@withCol, _mediaCheckResult.value?.unusedList ?: listOf()) }
        deletedFilesCount.value = deletedMedia
        _uiEvent.send(UiEvent.ShowDeletionResult)
    }
}
