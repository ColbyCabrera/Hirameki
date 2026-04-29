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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnErrorListener
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ManageNoteTypesUiState(
    val noteTypes: List<ManageNoteTypeUiModel> = emptyList(),
    val addOptions: List<AddNotetypeUiModel> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
)

class ManageNoteTypesViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()

    private val _allNoteTypes = MutableStateFlow<List<ManageNoteTypeUiModel>>(emptyList())
    private val _addOptions = MutableStateFlow<List<AddNotetypeUiModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<ManageNoteTypesUiState> = combine(
        _allNoteTypes, _addOptions, _searchQuery, _isLoading
    ) { noteTypes, addOptions, query, isLoading ->
        val filtered = if (query.isEmpty()) {
            noteTypes
        } else {
            noteTypes.filter { it.name.contains(query, ignoreCase = true) }
        }
        ManageNoteTypesUiState(
            noteTypes = filtered,
            addOptions = addOptions,
            searchQuery = query,
            isLoading = isLoading
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

    fun deleteNoteType(id: Long) {
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
