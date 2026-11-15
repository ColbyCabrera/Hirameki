// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.card_rendering.EmptyCardsReport
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchFailure
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchResult
import com.ichi2.anki.dialogs.EmptyCardsUiState.SearchingForEmptyCards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** @see EmptyCardsDialogFragment */
class EmptyCardsViewModel : ViewModel() {
    private val _uiState =
        MutableStateFlow<EmptyCardsUiState>(SearchingForEmptyCards)
    val uiState: StateFlow<EmptyCardsUiState> = _uiState.asStateFlow()

    fun searchForEmptyCards() {
        viewModelScope.launch {
            runCatching { withCol { getEmptyCards() } }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    _uiState.emit(EmptyCardsSearchFailure(exception))
                }.onSuccess { emptyCardsReport ->
                    _uiState.emit(EmptyCardsSearchResult(emptyCardsReport))
                }
        }
    }
}

sealed class EmptyCardsUiState {
    data object SearchingForEmptyCards : EmptyCardsUiState()

    data class EmptyCardsSearchResult(
        val emptyCardsReport: EmptyCardsReport,
    ) : EmptyCardsUiState()

    data class EmptyCardsSearchFailure(
        val throwable: Throwable,
    ) : EmptyCardsUiState()
}
