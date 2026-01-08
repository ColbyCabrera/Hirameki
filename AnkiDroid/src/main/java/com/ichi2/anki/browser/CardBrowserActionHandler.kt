/*
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.content.Intent
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.GradeNowDialog
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.undoAndShowSnackbar
import timber.log.Timber

/**
 * Helper class to handle common actions for the Card Browser.
 * This is used by both [com.ichi2.anki.CardBrowser] and [com.ichi2.anki.DeckPicker] (in tablet mode).
 */
class CardBrowserActionHandler(
    private val activity: AnkiActivity,
    private val viewModel: CardBrowserViewModel,
    private val launchEditCard: (Intent) -> Unit,
    private val launchAddNote: (Intent) -> Unit,
    private val launchPreview: (Intent) -> Unit
) {

    private suspend fun <T> withProgress(block: suspend () -> T): T {
        try {
            activity.showProgressBar()
            return block()
        } finally {
            activity.hideProgressBar()
        }
    }

    fun onSelectedTags(
        selectedTags: List<String>,
        @Suppress("UNUSED_PARAMETER") indeterminateTags: List<String>,
        @Suppress("UNUSED_PARAMETER") stateFilter: CardStateFilter
    ) {
        // _indeterminateTags and _stateFilter are used in the TagSelectionDialog to update UI state,
        // but they are not needed here in the action handler because viewModel.updateTags()
        // only requires the list of selected tags to perform the bulk update on the backend.
        // We suppress the UNUSED_PARAMETER warning instead of removing them to maintain consistency
        // with the TagsDialogListener interface, which this method signature mirrors.
        viewModel.updateTags(selectedTags)
    }

    fun onDeckSelected(deck: SelectableDeck?) {
        val did = (deck as? SelectableDeck.Deck)?.deckId ?: return
        moveSelectedCardsToDeck(did)
    }

    private fun moveSelectedCardsToDeck(did: DeckId) = activity.launchCatchingTask {
        val changed = withProgress { viewModel.moveSelectedCardsToDeck(did).await() }
        viewModel.search(viewModel.searchQuery.value)
        val message = activity.resources.getQuantityString(
            R.plurals.card_browser_cards_moved, changed.count, changed.count
        )
        activity.showSnackbar(message) {
            this.setAction(R.string.undo) { activity.launchCatchingTask { activity.undoAndShowSnackbar() } }
        }
    }

    fun openNoteEditorForCard(cardId: CardId) {
        viewModel.currentCardId = cardId
        val launcher = NoteEditorLauncher.EditCard(cardId, Direction.DEFAULT, false)
        launchEditCard(launcher.toIntent(activity))
    }

    fun showChangeDeckDialog() = activity.launchCatchingTask {
        if (!ensureSelection("Change Deck")) return@launchCatchingTask
        val selectableDecks = viewModel.getAvailableDecks()
        val dialog = DeckSelectionDialog.newInstance(
            activity.getString(R.string.move_all_to_deck), null, false, selectableDecks
        )
        dialog.show(activity.supportFragmentManager, "deck_selection_dialog")
    }

    fun rescheduleSelectedCards() {
        if (!ensureSelection("reschedule")) return
        if (warnUserIfInNotesOnlyMode()) return

        activity.launchCatchingTask {
            val allCardIds = viewModel.queryAllSelectedCardIds()
            SetDueDateDialog.newInstance(allCardIds)
                .show(activity.supportFragmentManager, "set_due_date_dialog")
        }
    }

    fun repositionSelectedCards() {
        Timber.i("CardBrowser:: Reposition button pressed")
        if (!ensureSelection("reposition")) return
        if (warnUserIfInNotesOnlyMode()) return
        activity.launchCatchingTask {
            when (val repositionCardsResult = viewModel.prepareToRepositionCards()) {
                is RepositionCardsRequest.ContainsNonNewCardsError -> {
                    SimpleMessageDialog.newInstance(
                        title = activity.getString(R.string.vague_error),
                        message = activity.getString(R.string.reposition_card_not_new_error),
                        reload = false
                    ).show(activity.supportFragmentManager, "reposition_error_dialog")
                    return@launchCatchingTask
                }

                is RepositionCardsRequest.RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        Timber.w("repositionSelectedCards: queueTop or queueBottom is null, aborting")
                        SimpleMessageDialog.newInstance(
                            title = activity.getString(R.string.vague_error),
                            message = activity.getString(R.string.card_browser_reposition_invalid_bounds),
                            reload = false
                        ).show(activity.supportFragmentManager, "reposition_invalid_bounds_dialog")
                        return@launchCatchingTask
                    }
                    val repositionDialog = RepositionCardFragment.newInstance(
                        queueTop = top,
                        queueBottom = bottom,
                        random = repositionCardsResult.random,
                        shift = repositionCardsResult.shift
                    )
                    repositionDialog.show(activity.supportFragmentManager, "reposition_dialog")
                }
            }
        }
    }

    fun onResetProgress() {
        if (!ensureSelection("reset progress")) return
        if (warnUserIfInNotesOnlyMode()) return
        ForgetCardsDialog().show(activity.supportFragmentManager, "reset_progress_dialog")
    }

    fun onGradeNow() {
        if (!ensureSelection("grade now")) return
        if (warnUserIfInNotesOnlyMode()) return
        activity.launchCatchingTask {
            val cids = viewModel.queryAllSelectedCardIds()
            GradeNowDialog.showDialog(activity, cids)
        }
    }

    fun exportSelected() {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds)
            .show(activity.supportFragmentManager, "exportDialog")
    }

    fun showEditTagsDialog() = activity.launchCatchingTask {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.d("showEditTagsDialog: called with empty selection")
            return@launchCatchingTask
        }

        val noteIds = viewModel.queryAllSelectedNoteIds()

        TagsDialog(activity as TagsDialogListener).withArguments(
                activity,
                TagsDialog.DialogType.EDIT_TAGS,
                noteIds
            ).show(activity.supportFragmentManager, "edit_tags_dialog")
    }

    fun showCreateFilteredDeckDialog() {
       viewModel.showCreateFilteredDeckDialog()
    }

    /**
     * If the user is in notes only mode, and there are notes selected,
     * show a snackbar explaining that the operation is not possible.
     * @return true if the user was warned, false otherwise.
     */
    fun warnUserIfInNotesOnlyMode(): Boolean {
        if (viewModel.cardsOrNotes == CardsOrNotes.NOTES && viewModel.hasSelectedAnyRows()) {
            activity.showSnackbar(
                activity.getString(R.string.card_browser_unavailable_when_notes_mode),
                duration = 5000
            ) {
                setAction(activity.getString(R.string.cards)) {
                    viewModel.setCardsOrNotes(CardsOrNotes.CARDS)
                }
            }
            return true
        }
        return false
    }

    fun addNote() {
        val launcher = NoteEditorLauncher.AddNoteFromCardBrowser(
            viewModel,
            inCardBrowserActivity = activity is com.ichi2.anki.CardBrowser
        )
        launchAddNote(launcher.toIntent(activity))
    }

    fun onPreview() {
        activity.launchCatchingTask {
            val intentData = viewModel.queryPreviewIntentData()
            val intent = PreviewerFragment.getIntent(
                activity, intentData.idsFile, intentData.currentIndex
            )
            launchPreview(intent)
        }
    }

    private fun ensureSelection(action: String): Boolean {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted $action - no cards selected")
            activity.showSnackbar(activity.getString(R.string.card_browser_no_cards_selected))
            return false
        }
        return true
    }
}
