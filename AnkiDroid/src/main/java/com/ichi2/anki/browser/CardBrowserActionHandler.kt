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
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.PreviewerFragment
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
    fun openNoteEditorForCard(cardId: CardId) {
        viewModel.currentCardId = cardId
        val launcher = NoteEditorLauncher.EditCard(cardId, Direction.DEFAULT, false)
        launchEditCard(launcher.toIntent(activity))
    }

    fun showChangeDeckDialog() {
        if (!ensureSelection("Change Deck")) return
        viewModel.showDeckSelectionDialog(true)
    }

    fun rescheduleSelectedCards() {
        if (!ensureSelection("reschedule")) return
        if (warnUserIfInNotesOnlyMode()) return
        viewModel.showSetDueDateDialog(true)
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
                        ).show(
                            activity.supportFragmentManager, "reposition_invalid_bounds_dialog"
                        )
                        return@launchCatchingTask
                    }
                    viewModel.showRepositionDialog(
                        CardBrowserViewModel.RepositionDialogState.Visible(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift
                        )
                    )
                }
            }
        }
    }

    fun onResetProgress() {
        if (!ensureSelection("reset progress")) return
        if (warnUserIfInNotesOnlyMode()) return
        viewModel.showForgetCardsDialog(true)
    }

    fun onGradeNow() {
        if (!ensureSelection("grade now")) return
        if (warnUserIfInNotesOnlyMode()) return
        viewModel.showGradeNowDialog(true)
    }

    fun exportSelected() {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds)
            .show(activity.supportFragmentManager, "exportDialog")
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
            viewModel.emitSnackbarMessage(
                activity.getString(R.string.card_browser_unavailable_when_notes_mode),
                activity.getString(R.string.cards)
            ) { viewModel.setCardsOrNotes(CardsOrNotes.CARDS) }
            return true
        }
        return false
    }

    fun addNote() {
        val launcher = NoteEditorLauncher.AddNoteFromCardBrowser(
            viewModel, inCardBrowserActivity = activity is com.ichi2.anki.CardBrowser
        )
        launchAddNote(launcher.toIntent(activity))
    }

    fun onPreview() {
        if (viewModel.rowCount == 0) {
            viewModel.emitSnackbarMessage(activity.getString(R.string.card_browser_no_cards_to_preview))
            return
        }
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
            viewModel.emitSnackbarMessage(activity.getString(R.string.card_browser_no_cards_selected))
            return false
        }
        return true
    }
}
