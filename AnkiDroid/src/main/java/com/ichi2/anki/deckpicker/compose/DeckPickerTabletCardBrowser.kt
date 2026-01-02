package com.ichi2.anki.deckpicker.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.compose.CardBrowserLayout
import com.ichi2.anki.browser.compose.FilterByTagsDialog
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.compose.FlagRenameDialog

@Composable
fun DeckPickerTabletCardBrowser(
    cardBrowserViewModel: CardBrowserViewModel,
    onNavigateToDecks: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onOpenNoteEditor: (Long) -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onAddNote: () -> Unit,
    onShowDialogFragment: (DialogFragment) -> Unit,
    onInvalidateOptionsMenu: () -> Unit,
) {
    BackHandler {
        onNavigateToDecks()
    }
    val allTagsState by cardBrowserViewModel.allTags.collectAsState()
    val selectedTags by cardBrowserViewModel.selectedTags.collectAsState()
    val deckTags by cardBrowserViewModel.deckTags.collectAsState()
    val filterTagsByDeck by cardBrowserViewModel.filterTagsByDeck.collectAsState()
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
                        rowId = CardOrNoteId(row.id),
                        topOffset = 0,
                    ),
                )
            } else {
                onOpenNoteEditor(row.id)
            }
        },
        onAddNote = { onAddNote() },
        onPreview = { /* ActionHandler handled by Activity */ }, // This might need a callback
        onFilter = cardBrowserViewModel::search,
        onSelectAll = { cardBrowserViewModel.toggleSelectAllOrNone() },
        onOptions = { showBrowserOptionsDialog = true },
        onCreateFilteredDeck = { onAddFilteredDeck() },
        onEditNote = {
            val cardId = cardBrowserViewModel.currentCardId
            if (cardId > 0) onOpenNoteEditor(cardId)
        },
        onCardInfo = {
            val cardId = cardBrowserViewModel.currentCardId
            if (cardId > 0) onOpenCardInfo(cardId)
        },
        onChangeDeck = { /* ActionHandler handled by Activity */ },
        onReposition = { /* ActionHandler handled by Activity */ },
        onSetDueDate = { /* ActionHandler handled by Activity */ },
        onGradeNow = { /* ActionHandler handled by Activity */ },
        onResetProgress = { /* ActionHandler handled by Activity */ },
        onExportCard = { /* ActionHandler handled by Activity */ },
        onFilterByTag = {
            cardBrowserViewModel.loadAllTags()
            cardBrowserViewModel.loadDeckTags()
            showFilterByTagsDialog = true
        })
}
