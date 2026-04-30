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
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.addNotetype
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.utils.getUserFriendlyErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface ManageNoteTypesUiEvent {
    /** Message is already localized and ready to display to the user. */
    data class ShowErrorMessage(val message: String) : ManageNoteTypesUiEvent
    data class ShowSnackbar(@StringRes val messageId: Int) : ManageNoteTypesUiEvent
    data class PromptSchemaChangeWarning(val noteType: ManageNoteTypeUiModel) :
        ManageNoteTypesUiEvent

    data class PromptDeleteSelectedConfirmation(val ids: Set<Long>) : ManageNoteTypesUiEvent
}

data class ManageNoteTypesUiState(
    val noteTypes: List<ManageNoteTypeUiModel> = emptyList(),
    val addOptions: List<AddNotetypeUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val deleteConfirmationNoteType: ManageNoteTypeUiModel? = null,
    val selectedNoteTypeIds: Set<Long> = emptySet(),
    val isInMultiSelectMode: Boolean = false,
)

class ManageNoteTypesViewModel(
    private val dispatcher: CoroutineDispatcher = ioDispatcher,
) : ViewModel() {
    private val _uiEvents = MutableSharedFlow<ManageNoteTypesUiEvent>()
    val uiEvents: SharedFlow<ManageNoteTypesUiEvent> = _uiEvents.asSharedFlow()

    private val _allNoteTypes = MutableStateFlow<List<ManageNoteTypeUiModel>>(emptyList())
    private val _addOptions = MutableStateFlow<List<AddNotetypeUiModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _deleteConfirmationNoteType = MutableStateFlow<ManageNoteTypeUiModel?>(null)
    private val _selectedNoteTypeIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ManageNoteTypesUiState> = combine(
        _allNoteTypes,
        _addOptions,
        _searchQuery,
        _isLoading,
        _deleteConfirmationNoteType,
        _selectedNoteTypeIds
    ) { values ->
        @Suppress("UNCHECKED_CAST") val noteTypes = values[0] as List<ManageNoteTypeUiModel>

        @Suppress("UNCHECKED_CAST") val addOptions = values[1] as List<AddNotetypeUiModel>
        val query = values[2] as String
        val isLoading = values[3] as Boolean
        val deleteConfirmationNoteType = values[4] as ManageNoteTypeUiModel?

        @Suppress("UNCHECKED_CAST") val selectedIds = values[5] as Set<Long>

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
            deleteConfirmationNoteType = deleteConfirmationNoteType,
            selectedNoteTypeIds = selectedIds,
            isInMultiSelectMode = selectedIds.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ManageNoteTypesUiState()
    )

    init {
        refresh()
    }

    private fun launchManageNoteTypesAction(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(dispatcher) {
            try {
                block()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Timber.w(exception)
                _isLoading.value = false
                _uiEvents.emit(
                    ManageNoteTypesUiEvent.ShowErrorMessage(
                        AnkiDroidApp.instance.getUserFriendlyErrorText(exception),
                    ),
                )
            }
        }

    fun refresh() {
        launchManageNoteTypesAction {
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
        launchManageNoteTypesAction {
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
        launchManageNoteTypesAction {
            val count = withCol { getNotetypeNames().size }
            if (count <= 1) {
                _uiEvents.emit(ManageNoteTypesUiEvent.ShowSnackbar(R.string.toast_last_model))
                return@launchManageNoteTypesAction
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
        launchManageNoteTypesAction {
            _isLoading.value = true
            withCol {
                removeNotetype(id)
            }
            refresh()
        }
    }

    fun renameNoteType(id: Long, newName: String) {
        launchManageNoteTypesAction {
            _isLoading.value = true
            withCol {
                val nt = getNotetype(id).toBuilder().setName(newName).build()
                updateNotetype(nt)
            }
            refresh()
        }
    }

    // region Multiselect

    fun toggleNoteTypeSelection(id: Long) {
        _selectedNoteTypeIds.value = _selectedNoteTypeIds.value.let { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun selectAllNoteTypes() {
        val visibleIds = uiState.value.noteTypes.map { it.id }.toSet()
        _selectedNoteTypeIds.value = visibleIds
    }

    fun deselectAllNoteTypes() {
        _selectedNoteTypeIds.value = emptySet()
    }

    fun deleteSelectedNoteTypes() {
        launchManageNoteTypesAction {
            val selectedIds = _selectedNoteTypeIds.value
            if (selectedIds.isEmpty()) return@launchManageNoteTypesAction

            val totalCount = withCol { getNotetypeNames().size }
            if (totalCount - selectedIds.size < 1) {
                _uiEvents.emit(ManageNoteTypesUiEvent.ShowSnackbar(R.string.toast_last_model))
                return@launchManageNoteTypesAction
            }
            _uiEvents.emit(ManageNoteTypesUiEvent.PromptDeleteSelectedConfirmation(selectedIds))
        }
    }

    fun confirmDeleteSelectedNoteTypes() {
        val idsToDelete = _selectedNoteTypeIds.value
        _selectedNoteTypeIds.value = emptySet()
        launchManageNoteTypesAction {
            _isLoading.value = true
            withCol {
                for (id in idsToDelete) {
                    removeNotetype(id)
                }
            }
            refresh()
        }
    }

    // endregion
}
