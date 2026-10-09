/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.customstudy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.dialogs.customstudy.CustomStudyTagsDialogFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class CustomStudyTagsViewModel(
    savedStateHandle: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val deckId: DeckId = checkNotNull(savedStateHandle[ARG_DECK_ID])

    private val _tagsState = MutableStateFlow<TagsState>(TagsState.Loading)
    val tagsState: StateFlow<TagsState> = _tagsState.asStateFlow()

    init {
        loadDeckTags()
    }

    fun loadDeckTags() {
        viewModelScope.launch(ioDispatcher) {
            _tagsState.value = TagsState.Loading
            val tags =
                try {
                    val defaults = withCol { sched.customStudyDefaults(deckId) }
                    defaults.tagsList
                        .map { it.name }
                        .filter { it.isNotBlank() && it != "tags" }
                        .sorted()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load custom study tags for deck %d", deckId)
                    emptyList()
                }
            _tagsState.value = TagsState.Loaded(tags)
        }
    }
}
