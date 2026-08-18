/*
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
 */

package com.ichi2.anki.deckpicker.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.compose.CardBrowserLayout
import com.ichi2.anki.browser.compose.FilterByTagsDialog
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.compose.FlagRenameDialog

/**
 * Tablet-only card browser surface shown from the deck picker navigation rail.
 *
 * This wraps [CardBrowserLayout] with the extra dialogs and callbacks needed when the browser is
 * embedded beside the deck picker instead of launched as a separate activity.
 */
@Composable
fun DeckPickerTabletCardBrowser(
    cardBrowserViewModel: CardBrowserViewModel,
    actionHandler: com.ichi2.anki.browser.CardBrowserActionHandler,
    onNavigateToDecks: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onAddNote: () -> Unit,
    onShowDialogFragment: (DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
) {
    BackHandler {
        onNavigateToDecks()
    }
    val allTagsState by cardBrowserViewModel.allTags.collectAsStateWithLifecycle()
    val selectedTags by cardBrowserViewModel.selectedTags.collectAsStateWithLifecycle()
    val deckTags by cardBrowserViewModel.deckTags.collectAsStateWithLifecycle()
    val filterTagsByDeck by cardBrowserViewModel.filterTagsByDeck.collectAsStateWithLifecycle()
    var showBrowserOptionsDialog by remember { mutableStateOf(false) }
    var showFilterByTagsDialog by remember { mutableStateOf(false) }
    var showFlagRenameDialog by remember { mutableStateOf(false) }

    if (showBrowserOptionsDialog) {
        BrowserOptionsDialog(
            onDismissRequest = {
                showBrowserOptionsDialog = false
            },
            onConfirm = { cardsOrNotes, isTruncated, shouldIgnoreAccents ->
                cardBrowserViewModel.setCardsOrNotes(
                    cardsOrNotes
                )
                cardBrowserViewModel.setTruncated(
                    isTruncated
                )
                cardBrowserViewModel.setIgnoreAccents(
                    shouldIgnoreAccents
                )
            },
            initialCardsOrNotes = cardBrowserViewModel.cardsOrNotes,
            initialIsTruncated = cardBrowserViewModel.isTruncated,
            initialShouldIgnoreAccents = cardBrowserViewModel.shouldIgnoreAccents,
            onManageColumnsClicked = {
                val dialog = BrowserColumnSelectionFragment.createInstance(
                    cardBrowserViewModel.cardsOrNotes
                )
                onShowDialogFragment(dialog)
            },
            onRenameFlagClicked = {
                showBrowserOptionsDialog = false
                showFlagRenameDialog = true
            },
        )
    }
    if (showFilterByTagsDialog) {
        FilterByTagsDialog(
            onDismissRequest = {
                showFilterByTagsDialog = false
            },
            onConfirm = { tags ->
                cardBrowserViewModel.filterByTags(tags)
                showFilterByTagsDialog = false
            },
            allTags = allTagsState,
            initialSelection = selectedTags,
            deckTags = deckTags,
            initialFilterByDeck = filterTagsByDeck,
            onFilterByDeckChanged = cardBrowserViewModel::setFilterTagsByDeck,
        )
    }
    if (showFlagRenameDialog) {
        FlagRenameDialog(
            onDismissRequest = {
                showFlagRenameDialog = false
                onInvalidateOptionsMenu()
            },
        )
    }

    CardBrowserLayout(
        viewModel = cardBrowserViewModel,
        fragmented = false, // Rail handled by DeckPicker
        onNavigateUp = onNavigateToDecks,
        onCardClicked = { row ->
            if (cardBrowserViewModel.isInMultiSelectMode) {
                cardBrowserViewModel.toggleRowSelection(
                    CardBrowserViewModel.RowSelection(
                        rowId = row.id,
                        topOffset = 0,
                    ),
                )
            } else {
                actionHandler.openNoteEditorForRow(row.id)
            }
        },
        onAddNote = onAddNote,
        onPreview = { actionHandler.onPreview() },
        onFilter = cardBrowserViewModel::search,
        onSelectAll = { cardBrowserViewModel.toggleSelectAllOrNone() },
        onOptions = { showBrowserOptionsDialog = true },
        onCreateFilteredDeck = { onAddFilteredDeck() },
        onEditNote = {
            actionHandler.openNoteEditorForSelectedRow()
        },
        onCardInfo = {
            actionHandler.openCardInfoForSelectedRow()
        },
        onChangeDeck = { actionHandler.showChangeDeckDialog() },
        onReposition = { actionHandler.repositionSelectedCards() },
        onSetDueDate = { actionHandler.rescheduleSelectedCards() },
        onGradeNow = { actionHandler.onGradeNow() },
        onResetProgress = { actionHandler.onResetProgress() },
        onExportCard = { actionHandler.exportSelected() },
        onFilterByTag = {
            cardBrowserViewModel.loadAllTags()
            cardBrowserViewModel.loadDeckTags()
            showFilterByTagsDialog = true
        })
}
