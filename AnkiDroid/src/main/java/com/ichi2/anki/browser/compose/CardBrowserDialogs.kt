/*
 Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.browser.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.scheduling.SetDueDateViewModel
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@Composable
fun CardBrowserDeckSelectionDialog(
    availableDecks: List<SelectableDeck.Deck>,
    onDeckSelected: (SelectableDeck.Deck) -> Unit,
    onDismissRequest: () -> Unit,
    onCreateDeck: () -> Unit,
    onCreateSubDeck: (DeckId) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val expandedDecks = remember { mutableStateMapOf<String, Boolean>() }

    val deckHierarchy = remember(availableDecks, searchQuery) {
        buildDeckHierarchyForDialog(availableDecks, searchQuery)
    }

    DisposableEffect(Unit) {
        onDispose {
            searchQuery = ""
            expandedDecks.clear()
        }
    }

    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.select_deck_title))
            IconButton(onClick = onCreateDeck) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.new_deck),
                )
            }
        }
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_deck)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
            )

            val rootChildren = deckHierarchy[""] ?: emptyList()
            val flatDeckList = remember(deckHierarchy, expandedDecks.toMap(), searchQuery) {
                buildFlatDeckList(
                    deckHierarchy = deckHierarchy,
                    children = rootChildren,
                    expandedDecks = expandedDecks,
                    searchQuery = searchQuery,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
            ) {
                items(
                    items = flatDeckList,
                    key = { it.deck.deckId },
                ) { flatItem ->
                    DeckRow(
                        flatItem = flatItem,
                        onDeckSelected = onDeckSelected,
                        onCreateSubDeck = onCreateSubDeck,
                        onToggleExpand = { deckName ->
                            expandedDecks[deckName] = !flatItem.isExpanded
                        },
                    )
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(R.string.dialog_cancel))
        }
    })
}

private data class FlatDeckItem(
    val deck: SelectableDeck.Deck,
    val depth: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean,
)

private fun buildFlatDeckList(
    deckHierarchy: Map<String, List<SelectableDeck.Deck>>,
    children: List<SelectableDeck.Deck>,
    expandedDecks: Map<String, Boolean>,
    searchQuery: String,
    flatList: MutableList<FlatDeckItem> = mutableListOf(),
): List<FlatDeckItem> {
    for (deck in children) {
        val isExpanded = expandedDecks[deck.name] ?: (searchQuery.isNotEmpty())
        val hasChildren = deckHierarchy.containsKey(deck.name)
        val parts = deck.name.split("::")
        val depth = parts.size - 1

        flatList.add(
            FlatDeckItem(
                deck = deck,
                depth = depth,
                hasChildren = hasChildren,
                isExpanded = isExpanded,
            ),
        )

        if (isExpanded && hasChildren) {
            val subChildren = deckHierarchy[deck.name] ?: emptyList()
            buildFlatDeckList(
                deckHierarchy = deckHierarchy,
                children = subChildren,
                expandedDecks = expandedDecks,
                searchQuery = searchQuery,
                flatList = flatList,
            )
        }
    }
    return flatList
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckRow(
    flatItem: FlatDeckItem,
    onDeckSelected: (SelectableDeck.Deck) -> Unit,
    onCreateSubDeck: (DeckId) -> Unit,
    onToggleExpand: (String) -> Unit,
) {
    val deck = flatItem.deck
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onDeckSelected(deck) },
                onLongClick = { onCreateSubDeck(deck.deckId) },
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width((flatItem.depth * 16).dp))

        if (flatItem.hasChildren) {
            Icon(
                painter = painterResource(
                    if (flatItem.isExpanded) {
                        R.drawable.keyboard_arrow_down_24px
                    } else {
                        R.drawable.keyboard_arrow_right_24px
                    },
                ),
                contentDescription = stringResource(
                    if (flatItem.isExpanded) R.string.collapse else R.string.expand,
                ),
                modifier = Modifier
                    .clickable { onToggleExpand(deck.name) }
                    .padding(4.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }

        Text(
            text = deck.getDisplayName(LocalContext.current),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun buildDeckHierarchyForDialog(
    decks: List<SelectableDeck.Deck>,
    searchQuery: String,
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
fun SetDueDateDialog(
    viewModel: SetDueDateViewModel,
    onHelpClicked: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isValid by viewModel.isValidFlow.collectAsStateWithLifecycle()
    val currentInterval by viewModel.currentInterval.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val singleDayText by viewModel.singleDayText.collectAsStateWithLifecycle()
    val startText by viewModel.startText.collectAsStateWithLifecycle()
    val endText by viewModel.endText.collectAsStateWithLifecycle()
    var updateInterval by remember { mutableStateOf(viewModel.updateIntervalToMatchDueDate) }

    LaunchedEffect(selectedTabIndex) {
        viewModel.currentTab = if (selectedTabIndex == 0) {
            SetDueDateViewModel.Tab.SINGLE_DAY
        } else {
            SetDueDateViewModel.Tab.DATE_RANGE
        }
    }

    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.sentence_set_due_date))
            IconButton(onClick = onHelpClicked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = stringResource(R.string.help),
                )
            }
        }
    }, text = {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selectedTabIndex == 0) {
                            Icon(
                                painter = painterResource(R.drawable.calendar_single_day),
                                contentDescription = null,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.single_day))
                }
                SegmentedButton(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selectedTabIndex == 1) {
                            Icon(
                                painter = painterResource(R.drawable.calendar_date_range),
                                contentDescription = null,
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.date_range))
                }
            }

            if (selectedTabIndex == 0) {
                OutlinedTextField(
                    value = singleDayText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            viewModel.singleDayText.value = newValue
                            viewModel.nextSingleDayDueDate = newValue.toIntOrNull()
                        }
                    },
                    label = {
                        Text(
                            pluralStringResource(
                                R.plurals.set_due_date_single_day_label,
                                viewModel.cardCount,
                                viewModel.cardCount,
                            ),
                        )
                    },
                    suffix = {
                        Text(
                            pluralStringResource(
                                R.plurals.set_due_date_label_suffix,
                                singleDayText.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.set_due_date_range_label,
                            viewModel.cardCount,
                            viewModel.cardCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    viewModel.startText.value = newValue
                                    viewModel.setNextDateRangeStart(newValue.toIntOrNull())
                                }
                            },
                            label = { Text(stringResource(R.string.set_due_date_range_start)) },
                            suffix = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.set_due_date_label_suffix,
                                        startText.toIntOrNull() ?: 0,
                                    ),
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    viewModel.endText.value = newValue
                                    viewModel.setNextDateRangeEnd(newValue.toIntOrNull())
                                }
                            },
                            label = { Text(stringResource(R.string.set_due_date_range_end)) },
                            suffix = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.set_due_date_label_suffix,
                                        endText.toIntOrNull() ?: 0,
                                    ),
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }

            if (viewModel.canSetUpdateIntervalToMatchDueDate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = updateInterval,
                        onCheckedChange = {
                            updateInterval = it
                            viewModel.updateIntervalToMatchDueDate = it
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.set_due_date_match_interval))
                }
            }

            currentInterval?.let { interval ->
                Text(
                    text = pluralStringResource(
                        R.plurals.set_due_date_current_interval,
                        interval,
                        interval,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = {
                onConfirm()
            },
            enabled = isValid,
        ) {
            Text(text = stringResource(R.string.dialog_ok))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(R.string.dialog_cancel))
        }
    })
}

@Composable
fun ForgetCardsDialog(
    onHelpClicked: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: (restorePosition: Boolean, resetCounts: Boolean) -> Unit,
) {
    var restorePosition by remember { mutableStateOf(true) }
    var resetCounts by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.reset_card_dialog_title))
            IconButton(onClick = onHelpClicked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = stringResource(R.string.help),
                )
            }
        }
    }, text = {
        val isInspection = LocalInspectionMode.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = restorePosition,
                        role = Role.Checkbox,
                        onValueChange = { restorePosition = it },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = restorePosition,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isInspection) "Restore position" else TR.schedulingRestorePosition())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = resetCounts,
                        role = Role.Checkbox,
                        onValueChange = { resetCounts = it },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = resetCounts,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isInspection) "Reset counts" else TR.schedulingResetCounts())
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            onConfirm(restorePosition, resetCounts)
            onDismissRequest()
        }) {
            Text(text = stringResource(R.string.dialog_ok))
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(R.string.dialog_cancel))
        }
    })
}

private data class GradeOption(
    val rating: Rating,
    val iconRes: Int,
    val label: String,
)

@Composable
fun GradeNowDialog(
    onConfirm: (Rating) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isInspection = LocalInspectionMode.current
    val options = remember(isInspection) {
        if (isInspection) {
            listOf(
                GradeOption(Rating.AGAIN, R.drawable.ic_ease_again, "Again"),
                GradeOption(Rating.HARD, R.drawable.ic_ease_hard, "Hard"),
                GradeOption(Rating.GOOD, R.drawable.ic_ease_good, "Good"),
                GradeOption(Rating.EASY, R.drawable.ic_ease_easy, "Easy"),
            )
        } else {
            listOf(
                GradeOption(Rating.AGAIN, R.drawable.ic_ease_again, TR.studyingAgain()),
                GradeOption(Rating.HARD, R.drawable.ic_ease_hard, TR.studyingHard()),
                GradeOption(Rating.GOOD, R.drawable.ic_ease_good, TR.studyingGood()),
                GradeOption(Rating.EASY, R.drawable.ic_ease_easy, TR.studyingEasy()),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.sentence_grade_now)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp),
            ) {
                items(options) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onConfirm(option.rating)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(option.iconRes),
                            contentDescription = null,
                            tint = Color.Unspecified,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
fun RepositionCardDialog(
    queueTop: Int,
    queueBottom: Int,
    initialRandom: Boolean,
    initialShift: Boolean,
    onConfirm: (position: Int, step: Int, random: Boolean, shift: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isInspection = LocalInspectionMode.current
    var startPositionText by rememberSaveable { mutableStateOf(queueTop.toString()) }
    var stepText by rememberSaveable { mutableStateOf("1") }
    var randomizeOrder by rememberSaveable { mutableStateOf(initialRandom) }
    var shiftPosition by rememberSaveable { mutableStateOf(initialShift) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.sentence_reposition_new_cards)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (isInspection) {
                        "Queue top: $queueTop\nQueue bottom: $queueBottom"
                    } else {
                        "${TR.browsingQueueTop(queueTop)}\n${TR.browsingQueueBottom(queueBottom)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = startPositionText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            startPositionText = newValue
                        }
                    },
                    label = {
                        Text(
                            if (isInspection) {
                                "Start position"
                            } else {
                                TR.browsingStartPosition().removeSuffix(":")
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = stepText,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            stepText = newValue
                        }
                    },
                    label = {
                        Text(
                            if (isInspection) "Step" else TR.browsingStep().removeSuffix(":"),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = randomizeOrder,
                        onCheckedChange = { randomizeOrder = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isInspection) "Randomize order" else TR.browsingRandomizeOrder())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = shiftPosition,
                        onCheckedChange = { shiftPosition = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isInspection) "Shift position of existing cards" else TR.browsingShiftPositionOfExistingCards())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pos = startPositionText.toIntOrNull() ?: queueTop
                    val step = stepText.toIntOrNull() ?: 1
                    onConfirm(pos, step, randomizeOrder, shiftPosition)
                    onDismissRequest()
                },
                enabled = startPositionText.toIntOrNull() != null && stepText.toIntOrNull() != null,
            ) {
                Text(text = stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Preview(
    name = "Card Browser Deck Selection Dialog",
    widthDp = 400,
    heightDp = 600,
    showBackground = true,
)
@Composable
private fun CardBrowserDeckSelectionDialogPreview() {
    AnkiDroidTheme {
        CardBrowserDeckSelectionDialog(
            availableDecks = listOf(
                SelectableDeck.Deck(1L, "Default"),
                SelectableDeck.Deck(2L, "Languages"),
                SelectableDeck.Deck(3L, "Languages::Spanish"),
                SelectableDeck.Deck(4L, "Languages::French"),
                SelectableDeck.Deck(5L, "Geography"),
            ),
            onDeckSelected = {},
            onDismissRequest = {},
            onCreateDeck = {},
            onCreateSubDeck = {},
        )
    }
}

@Preview(
    name = "Set Due Date Dialog - Single Day",
    widthDp = 400,
    heightDp = 450,
    showBackground = true,
)
@Composable
private fun SetDueDateDialogSingleDayPreview() {
    val viewModel = remember {
        SetDueDateViewModel().apply {
            cardIds = listOf(1L)
            currentInterval.value = 14
            nextSingleDayDueDate = 5
        }
    }
    AnkiDroidTheme {
        SetDueDateDialog(
            viewModel = viewModel,
            onHelpClicked = {},
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}

@Preview(
    name = "Set Due Date Dialog - Date Range",
    widthDp = 400,
    heightDp = 450,
    showBackground = true,
)
@Composable
private fun SetDueDateDialogDateRangePreview() {
    val viewModel = remember {
        SetDueDateViewModel().apply {
            cardIds = listOf(1L, 2L, 3L)
            currentTab = SetDueDateViewModel.Tab.DATE_RANGE
            setNextDateRangeStart(3)
            setNextDateRangeEnd(7)
        }
    }
    AnkiDroidTheme {
        SetDueDateDialog(
            viewModel = viewModel,
            onHelpClicked = {},
            onDismissRequest = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Forget Cards Dialog", widthDp = 400, heightDp = 250, showBackground = true)
@Composable
private fun ForgetCardsDialogPreview() {
    AnkiDroidTheme {
        ForgetCardsDialog(onHelpClicked = {}, onDismissRequest = {}, onConfirm = { _, _ -> })
    }
}

@Preview(name = "Grade Now Dialog", widthDp = 400, heightDp = 400, showBackground = true)
@Composable
private fun GradeNowDialogPreview() {
    AnkiDroidTheme {
        GradeNowDialog(onConfirm = {}, onDismissRequest = {})
    }
}

@Preview(name = "Reposition Card Dialog", widthDp = 400, heightDp = 600, showBackground = true)
@Composable
private fun RepositionCardDialogPreview() {
    AnkiDroidTheme {
        RepositionCardDialog(
            queueTop = 1,
            queueBottom = 100,
            initialRandom = true,
            initialShift = false,
            onConfirm = { _, _, _, _ -> },
            onDismissRequest = {},
        )
    }
}
