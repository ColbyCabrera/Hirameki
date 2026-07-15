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
package com.ichi2.anki.browser.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anki.search.BrowserRow
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserRowWithId
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.ColumnHeading
import com.ichi2.anki.dialogs.compose.DeleteConfirmationDialog
import com.ichi2.anki.dialogs.compose.TagsDialog
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.model.SortType
import kotlinx.coroutines.launch

private val ToolbarBottomSpacing = 32.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardBrowserScreen(
    viewModel: CardBrowserViewModel,
    onCardClicked: (BrowserRowWithId) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onAddNote: () -> Unit,
    onPreview: () -> Unit,
    onFilter: (String) -> Unit,
    onSelectAll: () -> Unit,
    onOptions: () -> Unit,
    onCreateFilteredDeck: () -> Unit,
    onEditNote: () -> Unit,
    onCardInfo: () -> Unit,
    onChangeDeck: () -> Unit,
    onReposition: () -> Unit,
    onSetDueDate: () -> Unit,
    onGradeNow: () -> Unit,
    onResetProgress: () -> Unit,
    onExportCard: () -> Unit,
    onFilterByTag: () -> Unit,
) {
    val browserRows by viewModel.browserRows.collectAsStateWithLifecycle()
    val columnHeadings by viewModel.flowOfColumnHeadings.collectAsStateWithLifecycle()
    val selectedRows by viewModel.flowOfSelectedRows.collectAsStateWithLifecycle(initialValue = emptySet())
    val hasSelection = selectedRows.isNotEmpty()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFlagMenu by remember { mutableStateOf(false) }
    var showSetFlagMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val layoutDirection = LocalLayoutDirection.current
    val toolbarHeightInDp = with(LocalDensity.current) { toolbarHeight.toDp() }
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val undoLabel = stringResource(R.string.undo)

    LifecycleResumeEffect(viewModel) {
        viewModel.notifyRefreshRequired()
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel.flowOfSnackbarMessage) {
        viewModel.flowOfSnackbarMessage.collect { messageRes ->
            snackbarHostState.showSnackbar(currentContext.getString(messageRes))
        }
    }

    LaunchedEffect(viewModel.flowOfSnackbarString) {
        viewModel.flowOfSnackbarString.collect { event ->
            val result = if (event.action != null) {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel ?: undoLabel,
                    duration = SnackbarDuration.Short,
                )
            } else {
                snackbarHostState.showSnackbar(event.message)
            }
            if (result == SnackbarResult.ActionPerformed) {
                event.action?.invoke()
            }
        }
    }

    LaunchedEffect(viewModel.flowOfDeleteResult) {
        viewModel.flowOfDeleteResult.collect { count ->
            val message = currentContext.resources.getQuantityString(
                R.plurals.card_browser_cards_deleted,
                count,
                count,
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    var showEditTagsDialog by remember { mutableStateOf(false) }

    if (showEditTagsDialog) {
        var tagsLoadState by remember {
            mutableStateOf<Map<String, CardBrowserViewModel.TagStatus>?>(
                null
            )
        }

        LaunchedEffect(Unit) {
            viewModel.loadAllTags()
            viewModel.loadDeckTags()
            tagsLoadState = viewModel.loadTagsForSelection()
        }

        val initialChecked =
            tagsLoadState?.filterValues { it == CardBrowserViewModel.TagStatus.CHECKED }?.keys
                ?: emptySet()
        val initialIndeterminate =
            tagsLoadState?.filterValues { it == CardBrowserViewModel.TagStatus.INDETERMINATE }?.keys
                ?: emptySet()

        val allTags by viewModel.allTags.collectAsStateWithLifecycle()
        val deckTags by viewModel.deckTags.collectAsStateWithLifecycle(initialValue = emptySet())

        TagsDialog(
            onDismissRequest = { showEditTagsDialog = false },
            onConfirm = { checked, indeterminate ->
                val initialPresent = initialChecked + initialIndeterminate
                val finalPresent = checked + indeterminate

                val added = checked - initialChecked
                val removed = initialPresent - finalPresent

                viewModel.saveTagsForSelection(added, removed)
                showEditTagsDialog = false
            },
            allTags = allTags,
            initialSelection = initialChecked,
            initialIndeterminate = initialIndeterminate,
            deckTags = deckTags,
            title = stringResource(R.string.menu_edit_tags),
            confirmButtonText = stringResource(R.string.dialog_ok),
            showFilterByDeckToggle = true,
            onAddTag = { /* Handled by dialog state internally for immediate display, not persisted until confirm */ },
        )
    }

    if (showDeleteConfirmationDialog) {
        DeleteConfirmationDialog(
            quantity = selectedRows.size,
            onDismissRequest = { showDeleteConfirmationDialog = false },
            onConfirm = {
                scope.launch {
                    viewModel.deleteSelectedNotes()
                    showDeleteConfirmationDialog = false
                }
            },
        )
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            CardBrowserHeader(columns = columnHeadings)
            HorizontalDivider()
            when (val state = searchState) {
                is SearchState.Initializing, is SearchState.Searching -> CardBrowserLoading(
                    Modifier.padding(
                        bottom = toolbarHeightInDp,
                    ),
                )

                is SearchState.Completed -> {
                    if (browserRows.isEmpty()) {
                        val selectedDeck by viewModel.flowOfDeckSelection.collectAsStateWithLifecycle(
                            null,
                        )
                        val deckName = when (val deck = selectedDeck) {
                            is SelectableDeck.Deck -> deck.name
                            else -> stringResource(R.string.card_browser_all_decks)
                        }
                        EmptyCardBrowser(deckName = deckName)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 0.dp,
                                bottom = toolbarHeightInDp + ToolbarBottomSpacing + contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            items(
                                items = browserRows,
                                key = { it.id },
                            ) { row ->
                                CardBrowserRow(
                                    row = row.browserRow,
                                    isSelected = selectedRows.contains(CardOrNoteId(row.id)),
                                    modifier = Modifier.combinedClickable(onClick = {
                                        onCardClicked(row)
                                    }, onLongClick = {
                                        viewModel.handleRowLongPress(
                                            CardBrowserViewModel.RowSelection(
                                                rowId = CardOrNoteId(row.id),
                                                topOffset = 0,
                                            ),
                                        )
                                    }),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }

                is SearchState.Error -> {
                    CardBrowserErrorState(error = state)
                }
            }
        }

        BrowserToolbar(
            onAddNote = onAddNote,
            onDeselect = { viewModel.deselectAll() },
            hasSelection = hasSelection,
            onPreview = onPreview,
            onSelectAll = onSelectAll,
            onFilter = { showFilterSheet = true },
            onMark = { viewModel.toggleMarkForSelectedRows() },
            onSetFlag = { showSetFlagMenu = true },
            onOptions = onOptions,
            onMoreOptions = { showMoreOptionsMenu = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .onSizeChanged { toolbarHeight = it.height },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = toolbarHeightInDp + ToolbarBottomSpacing + contentPadding.calculateBottomPadding(),
                ),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                actionColor = MaterialTheme.colorScheme.onSecondary,
            )
        }

        if (showFilterSheet) {
            FilterBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                onFilter = {
                    onFilter(it)
                },
                onFlagFilter = {
                    showFlagMenu = true
                },
                onFilterByTag = onFilterByTag,
            )
        }

        if (showMoreOptionsMenu) {
            MoreOptionsBottomSheet(
                onDismissRequest = { showMoreOptionsMenu = false },
                onChangeDisplayOrder = {
                    showMoreOptionsMenu = false
                    showSortMenu = true
                },
                onCreateFilteredDeck = {
                    onCreateFilteredDeck()
                    showMoreOptionsMenu = false
                },
                selectionCount = selectedRows.size,
                cardsOrNotes = viewModel.cardsOrNotes,
                onEditNote = {
                    onEditNote()
                    showMoreOptionsMenu = false
                },
                onDeleteNote = {
                    showDeleteConfirmationDialog = true
                    showMoreOptionsMenu = false
                },
                onCardInfo = {
                    onCardInfo()
                    showMoreOptionsMenu = false
                },
                onToggleSuspend = {
                    scope.launch {
                        viewModel.toggleSuspendCards()
                        showMoreOptionsMenu = false
                    }
                },
                onToggleBury = {
                    scope.launch {
                        viewModel.toggleBury()
                        showMoreOptionsMenu = false
                    }
                },
                onChangeDeck = {
                    onChangeDeck()
                    showMoreOptionsMenu = false
                },
                onReposition = {
                    onReposition()
                    showMoreOptionsMenu = false
                },
                onSetDueDate = {
                    onSetDueDate()
                    showMoreOptionsMenu = false
                },
                onEditTags = {
                    showEditTagsDialog = true
                    showMoreOptionsMenu = false
                },
                onGradeNow = {
                    onGradeNow()
                    showMoreOptionsMenu = false
                },
                onResetProgress = {
                    onResetProgress()
                    showMoreOptionsMenu = false
                },
                onExportCard = {
                    onExportCard()
                    showMoreOptionsMenu = false
                },
                onUndoDeleteNote = {
                    scope.launch {
                        viewModel.undo()
                        showMoreOptionsMenu = false
                    }
                },
            )
        }

        if (showSortMenu) {
            SelectableSortOrderBottomSheet(
                viewModel = viewModel,
                onDismiss = { showSortMenu = false },
            )
        }

        if (showFlagMenu) {
            FlagFilterBottomSheet(onDismiss = { showFlagMenu = false }, onFilter = {
                onFilter(it)
                showFlagMenu = false
            })
        }

        if (showSetFlagMenu) {
            SetFlagBottomSheet(onDismiss = { showSetFlagMenu = false }, onSetFlag = {
                viewModel.setFlagForSelectedRows(it)
                showSetFlagMenu = false
            })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowserToolbar(
    onAddNote: () -> Unit,
    onDeselect: () -> Unit,
    hasSelection: Boolean,
    onPreview: () -> Unit,
    onSelectAll: () -> Unit,
    onFilter: () -> Unit,
    onMark: () -> Unit,
    onSetFlag: () -> Unit,
    onOptions: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = if (hasSelection) onDeselect else onAddNote,
                shape = FloatingActionButtonDefaults.smallShape,
            ) {
                if (hasSelection) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                        ),
                        tooltip = {
                            PlainTooltip { Text(stringResource(R.string.card_browser_deselect_all)) }
                        },
                        state = rememberTooltipState(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.deselect_24px),
                            contentDescription = stringResource(R.string.card_browser_deselect_all),
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.add_24px),
                        contentDescription = stringResource(R.string.add_card),
                    )
                }
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip { Text(stringResource(R.string.card_editor_preview_card)) }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onPreview) {
                    Icon(
                        painter = painterResource(R.drawable.preview_24px),
                        contentDescription = stringResource(R.string.card_editor_preview_card),
                    )
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip { Text(stringResource(R.string.card_browser_select_all)) }
                },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        painter = painterResource(R.drawable.select_all_24px),
                        contentDescription = stringResource(R.string.card_browser_select_all),
                    )
                }
            }
            if (hasSelection) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.menu_mark_note)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onMark) {
                        Icon(
                            painter = painterResource(R.drawable.star_24px),
                            contentDescription = stringResource(R.string.menu_mark_note),
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.menu_flag)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onSetFlag) {
                        Icon(
                            painter = painterResource(R.drawable.flag_24px),
                            contentDescription = stringResource(R.string.menu_flag),
                        )
                    }
                }
            } else {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.filter)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onFilter) {
                        Icon(
                            painter = painterResource(R.drawable.filter_alt_24px),
                            contentDescription = stringResource(R.string.filter),
                        )
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                    ),
                    tooltip = {
                        PlainTooltip { Text(stringResource(R.string.browser_options_dialog_heading)) }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onOptions) {
                        Icon(
                            painter = painterResource(R.drawable.tune_24px),
                            contentDescription = stringResource(R.string.browser_options_dialog_heading),
                        )
                    }
                }
            }
            IconButton(onClick = onMoreOptions) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    onDismissRequest: () -> Unit,
    onFilter: (String) -> Unit,
    onFlagFilter: () -> Unit,
    onFilterByTag: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        ListItem(
            modifier = Modifier.clickable {
                onFilter("tag:marked")
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
            },
        ) { Text(stringResource(R.string.card_browser_show_marked)) }
        ListItem(
            modifier = Modifier.clickable {
                onFilter("is:suspended")
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
            },
        ) { Text(stringResource(R.string.card_browser_show_suspended)) }
        ListItem(
            modifier = Modifier.clickable {
                onFilterByTag()
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
            },
        ) { Text(stringResource(R.string.filter_by_tag)) }
        ListItem(
            modifier = Modifier.clickable { onFlagFilter() },
        ) { Text(stringResource(R.string.card_browser_search_by_flag)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    onDismissRequest: () -> Unit,
    onChangeDisplayOrder: () -> Unit,
    onCreateFilteredDeck: () -> Unit,
    selectionCount: Int,
    cardsOrNotes: CardsOrNotes,
    onEditNote: () -> Unit,
    onDeleteNote: () -> Unit,
    onCardInfo: () -> Unit,
    onToggleSuspend: () -> Unit,
    onToggleBury: () -> Unit,
    onChangeDeck: () -> Unit,
    onReposition: () -> Unit,
    onSetDueDate: () -> Unit,
    onEditTags: () -> Unit,
    onGradeNow: () -> Unit,
    onResetProgress: () -> Unit,
    onExportCard: () -> Unit,
    onUndoDeleteNote: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val hasSelection = selectionCount > 0

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        if (hasSelection) {
            if (selectionCount == 1) {
                ListItem(
                    modifier = Modifier.clickable { onEditNote() },
                ) { Text(stringResource(R.string.cardeditor_title_edit_card)) }
                ListItem(
                    modifier = Modifier.clickable { onCardInfo() },
                ) { Text(stringResource(R.string.card_info_title)) }
            }
            ListItem(
                modifier = Modifier.clickable { onDeleteNote() },
            ) {
                Text(
                    pluralStringResource(
                        R.plurals.card_browser_delete_notes, selectionCount
                    )
                )
            }
            ListItem(
                modifier = Modifier.clickable { onToggleSuspend() },
            ) { Text(stringResource(R.string.sentence_toggle_suspend)) }
            ListItem(
                modifier = Modifier.clickable { onToggleBury() },
            ) { Text(stringResource(R.string.sentence_toggle_bury)) }
            ListItem(
                modifier = Modifier.clickable { onChangeDeck() },
            ) { Text(stringResource(R.string.card_browser_change_deck)) }
            ListItem(
                modifier = Modifier.clickable { onReposition() },
            ) { Text(stringResource(R.string.card_editor_reposition_card)) }
            ListItem(
                modifier = Modifier.clickable { onSetDueDate() },
            ) { Text(stringResource(R.string.sentence_set_due_date)) }
            ListItem(
                modifier = Modifier.clickable { onEditTags() },
            ) { Text(stringResource(R.string.menu_edit_tags)) }
            ListItem(
                modifier = Modifier.clickable { onGradeNow() },
            ) { Text(stringResource(R.string.sentence_grade_now)) }
            ListItem(
                modifier = Modifier.clickable { onResetProgress() },
            ) { Text(stringResource(R.string.reset_progress)) }
            ListItem(
                modifier = Modifier.clickable { onExportCard() },
            ) {
                val exportStringRes = when (cardsOrNotes) {
                    CardsOrNotes.CARDS -> R.plurals.card_browser_export_cards
                    CardsOrNotes.NOTES -> R.plurals.card_browser_export_notes
                }
                Text(pluralStringResource(exportStringRes, selectionCount))
            }
        } else {
            ListItem(
                modifier = Modifier.clickable { onChangeDisplayOrder() },
            ) { Text(stringResource(R.string.card_browser_change_display_order)) }
            ListItem(
                modifier = Modifier.clickable {
                    onCreateFilteredDeck()
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            onDismissRequest()
                        }
                    }
                },
            ) { Text(stringResource(R.string.new_dynamic_deck)) }
            ListItem(
                modifier = Modifier.clickable { onUndoDeleteNote() },
            ) { Text(stringResource(R.string.undo_delete_note)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectableSortOrderBottomSheet(
    viewModel: CardBrowserViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val currentSortType by viewModel.sortTypeFlow.collectAsStateWithLifecycle()
    val isSortDescending by viewModel.isSortDescending.collectAsStateWithLifecycle()
    val sortLabels = stringArrayResource(id = R.array.card_browser_order_labels)

    val dismissSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                ButtonGroup(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    val sortOptions = listOf(
                        false to R.string.sort_order_ascending,
                        true to R.string.sort_order_descending,
                    )

                    sortOptions.forEach { (isDescending, textRes) ->
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                val isChecked = isSortDescending == isDescending
                                val shape = if (isDescending) {
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                } else {
                                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                                }
                                ToggleButton(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        viewModel.setSortDescending(isDescending)
                                        dismissSheet()
                                    },
                                    modifier = Modifier
                                        .weight(1F)
                                        .animateWidth(interactionSource),
                                    shapes = shape,
                                    interactionSource = interactionSource,
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            painterResource(R.drawable.check_24px),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(textRes),
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                    )
                                }
                            },
                        ) {}
                    }
                }
            }

            items(SortType.entries) { sortType ->
                val onItemClick: () -> Unit = {
                    viewModel.changeCardOrder(sortType)
                    dismissSheet()
                }
                ListItem(
                    leadingContent = {
                        RadioButton(
                            selected = currentSortType == sortType,
                            onClick = onItemClick,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onItemClick),
                ) { Text(text = sortLabels[sortType.cardBrowserLabelIndex]) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagFilterBottomSheet(
    onDismiss: () -> Unit,
    onFilter: (String) -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    var flagLabels by remember { mutableStateOf<Map<Flag, String>>(emptyMap()) }
    LaunchedEffect(true) {
        flagLabels = Flag.queryDisplayNames()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn {
            items(Flag.entries.filter { it != Flag.NONE }) { flag ->
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.flag_24px),
                            contentDescription = stringResource(R.string.card_browser_search_by_flag),
                            tint = colorResource(
                                id = flag.browserColorRes ?: R.color.transparent,
                            ),
                        )
                    },
                    modifier = Modifier.clickable {
                        onFilter("flag:${flag.code}")
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismiss()
                            }
                        }
                    },
                ) { Text(flagLabels[flag] ?: "") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetFlagBottomSheet(
    onDismiss: () -> Unit,
    onSetFlag: (Flag) -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    var flagLabels by remember { mutableStateOf<Map<Flag, String>>(emptyMap()) }
    LaunchedEffect(true) {
        flagLabels = Flag.queryDisplayNames()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn {
            items(Flag.entries) { flag ->
                ListItem(
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.flag_24px),
                            contentDescription = stringResource(R.string.menu_flag),
                            tint = if (flag == Flag.NONE) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                colorResource(
                                    id = flag.browserColorRes ?: R.color.transparent,
                                )
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        onSetFlag(flag)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismiss()
                            }
                        }
                    },
                ) { Text(flagLabels[flag] ?: "") }
            }
        }
    }
}

@Composable
fun EmptyCardBrowser(
    modifier: Modifier = Modifier,
    deckName: String,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardBrowserEmpty(deckName = deckName)
    }
}

@Composable
fun CardBrowserErrorState(
    modifier: Modifier = Modifier,
    error: SearchState.Error,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardBrowserError(error = error)
    }
}

@Composable
fun CardBrowserError(
    modifier: Modifier = Modifier,
    error: SearchState.Error,
) {
    Text(
        text = (stringResource(id = R.string.vague_error) + ": " + error.error),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardBrowserLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(
            color = LoadingIndicatorDefaults.indicatorColor,
            polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
        )
    }
}

@Composable
fun CardBrowserEmpty(
    deckName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(id = R.string.card_browser_no_cards_in_deck, deckName),
        modifier = modifier,
    )
}

@Composable
fun CardBrowserHeader(columns: List<ColumnHeading>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        columns.forEach { column ->
            Text(
                text = column.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CardBrowserRow(
    row: BrowserRow,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor: Color = when {
        isSelected && row.color == BrowserRow.Color.COLOR_DEFAULT -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.primary

        row.color != BrowserRow.Color.COLOR_DEFAULT -> {
            when (row.color) {
                BrowserRow.Color.COLOR_MARKED -> MaterialTheme.colorScheme.tertiaryContainer
                BrowserRow.Color.COLOR_FLAG_RED -> colorResource(Flag.RED.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_ORANGE -> colorResource(Flag.ORANGE.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_GREEN -> colorResource(Flag.GREEN.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_BLUE -> colorResource(Flag.BLUE.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_PINK -> colorResource(Flag.PINK.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_TURQUOISE -> colorResource(Flag.TURQUOISE.browserColorRes!!)
                BrowserRow.Color.COLOR_FLAG_PURPLE -> colorResource(Flag.PURPLE.browserColorRes!!)
                else -> MaterialTheme.colorScheme.surface
            }
        }

        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor: Color = when (backgroundColor) {
        MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimary
        MaterialTheme.colorScheme.primaryContainer -> MaterialTheme.colorScheme.onPrimaryContainer
        MaterialTheme.colorScheme.tertiaryContainer -> MaterialTheme.colorScheme.onTertiaryContainer
        colorResource(Flag.RED.browserColorRes!!), colorResource(Flag.ORANGE.browserColorRes!!),
        colorResource(
            Flag.GREEN.browserColorRes!!,
        ),
        colorResource(Flag.BLUE.browserColorRes!!), colorResource(Flag.PINK.browserColorRes!!),
        colorResource(
            Flag.TURQUOISE.browserColorRes!!,
        ),
        colorResource(Flag.PURPLE.browserColorRes!!),
            -> Color.Black

        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            row.cellsList.forEach { cell ->
                Text(
                    text = cell.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
