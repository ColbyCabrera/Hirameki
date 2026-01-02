/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                      *
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

package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A shared deck selection component that shows the currently selected deck and allows
 * picking a new one from a hierarchical dropdown menu.
 */
@Composable
fun DeckSelector(
    selectedDeck: SelectableDeck?,
    availableDecks: List<SelectableDeck.Deck>,
    onDeckSelected: (SelectableDeck) -> Unit,
    modifier: Modifier = Modifier,
    showAllDecksOption: Boolean = true
) {
    var showDeckMenu by remember { mutableStateOf(false) }
    var deckSearchQuery by remember { mutableStateOf("") }
    val expandedDecks = remember { mutableStateMapOf<String, Boolean>() }

    val deckHierarchy = remember(availableDecks, deckSearchQuery) {
        buildDeckHierarchy(availableDecks, deckSearchQuery)
    }

    // Clean up state when deck menu is dismissed
    DisposableEffect(showDeckMenu) {
        onDispose {
            if (!showDeckMenu) {
                deckSearchQuery = ""
                expandedDecks.clear()
            }
        }
    }

    val deckName = when (selectedDeck) {
        is SelectableDeck.Deck -> selectedDeck.name
        is SelectableDeck.AllDecks -> stringResource(R.string.card_browser_all_decks)
        else -> ""
    }

    Column(modifier = modifier) {
        TextButton(onClick = { showDeckMenu = true }) {
            Text(
                text = deckName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.select_deck)
            )
        }

        DropdownMenu(
            expanded = showDeckMenu,
            onDismissRequest = { showDeckMenu = false },
            shape = MaterialTheme.shapes.large
        ) {
            Surface(
                modifier = Modifier.padding(
                    vertical = 8.dp, horizontal = 12.dp
                ),
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = deckSearchQuery,
                        onValueChange = { deckSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.card_browser_search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.card_browser_search_hint)
                            )
                        },
                        trailingIcon = {
                            if (deckSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { deckSearchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close)
                                    )
                                }
                            }
                        },
                        colors = transparentTextFieldColors(),
                    )
                }
            }

            if (showAllDecksOption) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.card_browser_all_decks)) },
                    onClick = {
                        onDeckSelected(SelectableDeck.AllDecks)
                        showDeckMenu = false
                    }
                )
            }

            DeckHierarchyMenu(
                deckHierarchy = deckHierarchy,
                expandedDecks = expandedDecks,
                onDeckSelected = {
                    onDeckSelected(it)
                    showDeckMenu = false
                },
                searchQuery = deckSearchQuery
            )
        }
    }
}

@Composable
private fun transparentTextFieldColors(): TextFieldColors =
    TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    )

private fun buildDeckHierarchy(
    decks: List<SelectableDeck.Deck>,
    searchQuery: String
): Map<String, List<SelectableDeck.Deck>> {
    val hierarchy = mutableMapOf<String, MutableList<SelectableDeck.Deck>>()
    val topLevelDecks = mutableListOf<SelectableDeck.Deck>()

    val decksToShow = if (searchQuery.isEmpty()) {
        decks
    } else {
        val matchingDecks = decks.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val requiredDecks = mutableSetOf<SelectableDeck.Deck>()
        val allDecksByName = decks.associateBy { it.name }

        for (deck in matchingDecks) {
            requiredDecks.add(deck)
            var currentName = deck.name
            while (currentName.contains("::")) {
                currentName = currentName.substringBeforeLast("::")
                allDecksByName[currentName]?.let { requiredDecks.add(it) }
            }
        }
        requiredDecks.toList()
    }

    for (deck in decksToShow) {
        val parts = deck.name.split("::")
        if (parts.size > 1) {
            val parentName = parts.dropLast(1).joinToString("::")
            hierarchy.getOrPut(parentName) { mutableListOf() }.add(deck)
        } else {
            topLevelDecks.add(deck)
        }
    }

    hierarchy[""] = topLevelDecks
    return hierarchy
}

@Composable
private fun DeckHierarchyMenu(
    deckHierarchy: Map<String, List<SelectableDeck.Deck>>,
    expandedDecks: MutableMap<String, Boolean>,
    onDeckSelected: (SelectableDeck.Deck) -> Unit,
    searchQuery: String,
    parentName: String = ""
) {
    val children = deckHierarchy[parentName] ?: return

    for (deck in children) {
        val isExpanded = expandedDecks[deck.name] ?: false
        val hasChildren = deckHierarchy.containsKey(deck.name)

        DropdownMenuItem(
            text = { Text(deck.name.substringAfterLast("::")) },
            onClick = { onDeckSelected(deck) },
            trailingIcon = {
                if (hasChildren) {
                    IconButton(onClick = { expandedDecks[deck.name] = !isExpanded }) {
                        Icon(
                            painter = if (isExpanded) painterResource(R.drawable.keyboard_arrow_down_24px)
                            else painterResource(R.drawable.keyboard_arrow_right_24px),
                            contentDescription = stringResource(
                                if (isExpanded) R.string.collapse else R.string.expand
                            )
                        )
                    }
                }
            }
        )
        if (isExpanded && hasChildren) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                DeckHierarchyMenu(
                    deckHierarchy, expandedDecks, onDeckSelected, searchQuery, deck.name
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeckSelectorPreview() {
    val availableDecks = listOf(
        SelectableDeck.Deck(1L, "Default"),
        SelectableDeck.Deck(2L, "Japanese"),
        SelectableDeck.Deck(3L, "Japanese::Kanji"),
        SelectableDeck.Deck(4L, "Japanese::Vocabulary"),
        SelectableDeck.Deck(5L, "Spanish"),
    )
    var selectedDeck by remember { mutableStateOf<SelectableDeck?>(SelectableDeck.AllDecks) }

    AnkiDroidTheme {
        DeckSelector(
            selectedDeck = selectedDeck,
            availableDecks = availableDecks,
            onDeckSelected = { selectedDeck = it }
        )
    }
}
