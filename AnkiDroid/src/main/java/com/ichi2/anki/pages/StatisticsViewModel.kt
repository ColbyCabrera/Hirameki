/***************************************************************************************
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
 **********************************************************************************/

package com.ichi2.anki.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.model.SelectableDeck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject.quote
import timber.log.Timber

/**
 * ViewModel for [StatisticsScreen] to manage deck selection and JavaScript injection.
 */
class StatisticsViewModel : ViewModel() {
    private val _availableDecks = MutableStateFlow<List<SelectableDeck.Deck>>(emptyList())
    val availableDecks = _availableDecks.asStateFlow()

    private val _selectedDeck = MutableStateFlow<SelectableDeck?>(null)
    val selectedDeck = _selectedDeck.asStateFlow()

    private val _jsInjectionEvent = MutableSharedFlow<String>()
    val jsInjectionEvent = _jsInjectionEvent.asSharedFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        loadDecks()
    }

    private fun loadDecks() {
        viewModelScope.launch {
            try {
                val deckValues = withCol {
                    decks.allNamesAndIds(includeFiltered = false, skipEmptyDefault = true)
                        .map { SelectableDeck.Deck(it.id, it.name) }
                }
                _availableDecks.value = deckValues

                // Set initial deck to currently selected deck in collection
                val currentDeckId = withCol { decks.selected() }
                val currentDeck = deckValues.find { it.deckId == currentDeckId }
                if (currentDeck != null) {
                    selectDeck(currentDeck)
                } else if (deckValues.isNotEmpty()) {
                    selectDeck(deckValues.first())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load decks for statistics")
                _snackbarMessage.emit("Failed to load decks")
            }
        }
    }

    fun selectDeck(deck: SelectableDeck) {
        if (_selectedDeck.value == deck) return

        _selectedDeck.update { deck }

        if (deck is SelectableDeck.Deck) {
            injectDeckChangeScript(deck.name)
        }
    }

    private fun injectDeckChangeScript(deckName: String) {
        val escapedDeckName = quote(deckName)

        val javascriptCode = """
            function setDeck(retries) {
                var textBox = document.getElementById("statisticsSearchText");
                if (textBox) {
                    textBox.value = "deck:" + $escapedDeckName;
                    textBox.dispatchEvent(new Event("input", { bubbles: true }));
                    textBox.dispatchEvent(new Event("change"));
                } else if (retries > 0) {
                    setTimeout(function() { setDeck(retries - 1); }, 200);
                }
            }
            setDeck(5);
        """.trimIndent()

        viewModelScope.launch {
            _jsInjectionEvent.emit(javascriptCode)
        }
    }
}
