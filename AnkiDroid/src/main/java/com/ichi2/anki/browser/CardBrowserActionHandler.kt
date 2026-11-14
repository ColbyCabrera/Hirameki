// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
    private val launchPreview: (Intent) -> Unit,
) {
    fun openNoteEditorForCard(cardId: CardId) {
        val launcher = NoteEditorLauncher.EditCard(cardId, Direction.DEFAULT, false)
        launchEditCard(launcher.toIntent(activity))
    }

    fun openNoteEditorForRow(rowId: CardOrNoteId): kotlinx.coroutines.Job =
        activity.launchCatchingTask {
            val cardId = viewModel.queryDataForCardEdit(rowId)
            if (cardId != null) {
                openNoteEditorForCard(cardId)
            } else {
                Timber.w("openNoteEditorForRow: Could not resolve CardId for %s", rowId)
                viewModel.emitSnackbarMessage(activity.getString(R.string.no_note_to_edit))
            }
        }

    fun openNoteEditorForSelectedRow(): kotlinx.coroutines.Job? {
        val selectedId = viewModel.selectedRows.firstOrNull()
        return if (selectedId != null) {
            openNoteEditorForRow(selectedId)
        } else {
            ensureSelection("edit note")
            null
        }
    }

    fun openCardInfoForSelectedRow(): kotlinx.coroutines.Job? {
        if (!ensureSelection("card info")) return null

        return activity.launchCatchingTask {
            val destination = viewModel.queryCardInfoDestination()
            if (destination != null) {
                activity.startActivity(destination.toIntent(activity))
            } else {
                Timber.w("openCardInfoForSelectedRow: Could not resolve CardId for selected row")
                viewModel.emitSnackbarMessage(activity.getString(R.string.no_note_to_edit))
            }
        }
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
                    SimpleMessageDialog
                        .newInstance(
                            title = activity.getString(R.string.vague_error),
                            message = activity.getString(R.string.reposition_card_not_new_error),
                            reload = false,
                        ).show(activity.supportFragmentManager, "reposition_error_dialog")
                    return@launchCatchingTask
                }

                is RepositionCardsRequest.RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        Timber.w("repositionSelectedCards: queueTop or queueBottom is null, aborting")
                        SimpleMessageDialog
                            .newInstance(
                                title = activity.getString(R.string.vague_error),
                                message = activity.getString(R.string.card_browser_reposition_invalid_bounds),
                                reload = false,
                            ).show(
                                activity.supportFragmentManager,
                                "reposition_invalid_bounds_dialog",
                            )
                        return@launchCatchingTask
                    }
                    viewModel.showRepositionDialog(
                        CardBrowserViewModel.RepositionDialogState.Visible(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift,
                        ),
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
        ExportDialogFragment
            .newInstance(type, selectedIds)
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
                activity.getString(R.string.cards),
            ) { viewModel.setCardsOrNotes(CardsOrNotes.CARDS) }
            return true
        }
        return false
    }

    fun addNote() {
        val launcher =
            NoteEditorLauncher.AddNoteFromCardBrowser(
                viewModel,
                inCardBrowserActivity = activity is com.ichi2.anki.CardBrowser,
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
            val intent =
                PreviewerFragment.getIntent(
                    activity,
                    intentData.idsFile,
                    intentData.currentIndex,
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
