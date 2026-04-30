/****************************************************************************************
 * Copyright (c) 2025 AnkiDroid Open Source Team                                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                       *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                                 *
 * See the GNU General Public License for more details.                                 *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.notetype

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.addNotetype
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.libanki.updateNotetype
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ManageNoteTypesUiEvent {
    data class ShowSnackbar(@StringRes val messageId: Int) : ManageNoteTypesUiEvent
    data class PromptSchemaChangeWarning(val noteType: ManageNoteTypeUiModel) :
        ManageNoteTypesUiEvent
}

data class ManageNoteTypesUiState(
    val noteTypes: List<ManageNoteTypeUiModel> = emptyList(),
    val addOptions: List<AddNotetypeUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val deleteConfirmationNoteType: ManageNoteTypeUiModel? = null
)

class ManageNoteTypesViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()

    private val _uiEvents = MutableSharedFlow<ManageNoteTypesUiEvent>()
    val uiEvents: SharedFlow<ManageNoteTypesUiEvent> = _uiEvents.asSharedFlow()

    private val _allNoteTypes = MutableStateFlow<List<ManageNoteTypeUiModel>>(emptyList())
    private val _addOptions = MutableStateFlow<List<AddNotetypeUiModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _deleteConfirmationNoteType = MutableStateFlow<ManageNoteTypeUiModel?>(null)

    val uiState: StateFlow<ManageNoteTypesUiState> = combine(
        _allNoteTypes, _addOptions, _searchQuery, _isLoading, _deleteConfirmationNoteType
    ) { noteTypes, addOptions, query, isLoading, deleteConfirmationNoteType ->
        val filtered = if (query.isEmpty()) {
            noteTypes
        } else {
            noteTypes.filter { it.name.contains(query, ignoreCase = true) }
        }
        ManageNoteTypesUiState(
            noteTypes = filtered,
            addOptions = addOptions,
            searchQuery = query,
            isLoading = isLoading,
            deleteConfirmationNoteType = deleteConfirmationNoteType
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageNoteTypesUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        launchCatchingIO {
            _isLoading.value = true
            val (updated, options) = withCol {
                val types = getNotetypeNameIdUseCount().map { it.toUiModel() }
                val standardNotetypesModels =
                    StockNotetype.Kind.entries.filter { it != StockNotetype.Kind.UNRECOGNIZED }
                        .map {
                            AddNotetypeUiModel(
                                id = it.number.toLong(),
                                name = getStockNotetype(it).name,
                                isStandard = true,
                            )
                        }
                val currentNotetypes = getNotetypeNames().map { it.toUiModel() }
                val allOptions = standardNotetypesModels + currentNotetypes
                Pair(types, allOptions)
            }
            _allNoteTypes.value = updated
            _addOptions.value = options
            _isLoading.value = false
        }
    }

    fun addNoteType(newName: String, selectedOption: AddNotetypeUiModel) {
        launchCatchingIO {
            _isLoading.value = true
            withCol {
                if (selectedOption.isStandard) {
                    val kind = StockNotetype.Kind.forNumber(selectedOption.id.toInt())
                    val updatedStandardNotetype = getStockNotetype(kind).apply {
                        name = newName
                    }
                    addNotetypeLegacy(BackendUtils.toJsonBytes(updatedStandardNotetype))
                } else {
                    val targetNotetype = getNotetype(selectedOption.id)
                    val newNotetype = targetNotetype.copy {
                        id = 0
                        name = newName
                    }
                    addNotetype(newNotetype)
                }
            }
            refresh()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun requestDeleteNoteType(noteType: ManageNoteTypeUiModel) {
        launchCatchingIO {
            val count = withCol { getNotetypeNames().size }
            if (count <= 1) {
                _uiEvents.emit(ManageNoteTypesUiEvent.ShowSnackbar(R.string.toast_last_model))
                return@launchCatchingIO
            }
            _uiEvents.emit(ManageNoteTypesUiEvent.PromptSchemaChangeWarning(noteType))
        }
    }

    fun showDeleteConfirmation(noteType: ManageNoteTypeUiModel) {
        _deleteConfirmationNoteType.value = noteType
    }

    fun dismissDeleteConfirmation() {
        _deleteConfirmationNoteType.value = null
    }

    fun confirmDeleteNoteType(id: Long) {
        _deleteConfirmationNoteType.value = null
        launchCatchingIO {
            _isLoading.value = true
            withCol {
                removeNotetype(id)
            }
            refresh()
        }
    }

    fun renameNoteType(id: Long, newName: String) {
        launchCatchingIO {
            _isLoading.value = true
            withCol {
                val nt = getNotetype(id).toBuilder().setName(newName).build()
                updateNotetype(nt)
            }
            refresh()
        }
    }
}
