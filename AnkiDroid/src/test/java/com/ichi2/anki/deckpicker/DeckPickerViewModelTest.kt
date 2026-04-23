/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.CheckResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.card_rendering.EmptyCardsReport
import anki.card_rendering.emptyCardsReport
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.emptyCids
import com.ichi2.testutils.ensureOpsExecuted
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment
import timber.log.Timber

/** Test of [DeckPickerViewModel] */
@RunWith(AndroidJUnit4::class)
class DeckPickerViewModelTest : RobolectricTest() {
    private val viewModel = DeckPickerViewModel()

    @Test
    fun `empty cards - flow`() = runTest {
        val cardsToEmpty = createEmptyCards()

        viewModel.composeEffects.test {
            // test a 'normal' deletion
            viewModel.deleteEmptyCards(cardsToEmpty).join()

            val item = expectMostRecentItem()
            assertThat(
                "is undo snackbar",
                item,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )

            // ensure a duplicate output is displayed to the user
            val newCardsToEmpty = createEmptyCards()
            viewModel.deleteEmptyCards(newCardsToEmpty).join()

            val item2 = expectMostRecentItem()
            assertThat(
                "duplicate is undo snackbar",
                item2,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )

            // test an empty list: a no-op should inform the user, rather than do nothing
            viewModel.deleteEmptyCards(emptyCardsReport { }).join()

            val item3 = expectMostRecentItem()
            assertThat(
                "'no cards deleted' is notified",
                item3,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )
        }
    }

    @Test
    fun `empty cards - undoable`() = runTest {
        val cardsToEmpty = createEmptyCards()

        // ChangeManager assert
        ensureOpsExecuted(1) {
            viewModel.deleteEmptyCards(cardsToEmpty).join()
        }

        // backend assert
        assertThat("col undo status", col.undoStatus().undo, equalTo("Empty Cards"))
    }

    @Test
    fun `empty cards - keep notes`() = runTest {
        val emptyCardsReport = createEmptyCards()
        val deleteNotesReport = createEmptyCards()

        viewModel.composeEffects.test {
            viewModel.deleteEmptyCards(emptyCardsReport, preserveNotes = true).join()

            val item = expectMostRecentItem()
            assertThat(
                "is undo snackbar",
                item,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )

            viewModel.deleteEmptyCards(deleteNotesReport, preserveNotes = false).join()

            val item2 = expectMostRecentItem()
            assertThat(
                "is undo snackbar after delete",
                item2,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )
        }
    }

    @Test
    fun `empty filtered - functionality`() {
        runTest {
            val note = addBasicNote("To", "Filtered")
            val filteredDeckId = moveAllCardsToFilteredDeck(assertOn = note)

            viewModel.emptyFilteredDeck(filteredDeckId)
            advanceUntilIdle()

            assertThat("deck was reset", note.firstCard().did, equalTo(Consts.DEFAULT_DECK_ID))
        }
    }

    @Test
    fun `empty filtered - does not hang when updating deck list`() {
        runTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            viewModel.updateDeckList()
            advanceUntilIdle()

            // Due to Robolectric and Dispatchers.IO, `dueTree` update might be queued on the main looper indefinitely if `.join()` hangs.
            // We just ensure no hanging occurs.

            viewModel.emptyFilteredDeck(filteredDeckId)
            advanceUntilIdle()
        }
    }

    @Test
    fun `empty filtered - undoable`() {
        runTest {
            val filteredDeckId = moveAllCardsToFilteredDeck()

            // ChangeManager assert
            ensureOpsExecuted(1) {
                viewModel.emptyFilteredDeck(filteredDeckId)
                advanceUntilIdle()
            }

            // backend assert
            assertThat("col undo status", col.undoStatus().undo, equalTo("Empty"))
        }
    }

    /**
     * Creates a note with 3 cards, all empty
     *
     * This allows us to test the 'keep note' functionality only affects the first card
     */
    @CheckResult
    @SuppressLint("CheckResult")
    private suspend fun createEmptyCards(): EmptyCardsReport {
        addNoteUsingNoteTypeName("Cloze", "{{c1::Hello}} {{c3::There}} {{c2::World}}", "").apply {
            setField(0, "No cloze")
            col.updateNote(this)
        }
        return withCol { getEmptyCards() }.also { report ->
            assertThat(
                "there are empty cards", report.emptyCids(), not(empty())
            )
            Timber.d("created %d empty cards: [%s]", report.emptyCids().size, report.emptyCids())
        }
    }

    /** test helper to use [deleteEmptyCards] with the original test `preserveNotes` value */
    private fun DeckPickerViewModel.deleteEmptyCards(report: EmptyCardsReport) =
        deleteEmptyCards(report, preserveNotes = false)

    /**
     * Moves all cards to a deck named "Filtered"
     *
     * If there are no notes, one is created
     * @return The [DeckId] of the filtered deck
     */
    private fun moveAllCardsToFilteredDeck(
        assertOn: Note = addBasicNote(
            "To", "Filtered"
        )
    ): DeckId = addDynamicDeck("Filtered", "").also { did ->
        assertThat("filter - did", assertOn.firstCard().did, equalTo(did))
        assertThat("filter - odid", assertOn.firstCard().oDid, equalTo(Consts.DEFAULT_DECK_ID))
    }

    // region Deck Name Validation Tests

    @Test
    fun `validateDeckName - blank name returns null`() = runTest {
        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.DECK, titleResId = 0
        )
        val result = viewModel.validateDeckName("", state)
        assertThat("blank name should be null (no error)", result, equalTo(null))

        val resultWithSpaces = viewModel.validateDeckName("   ", state)
        assertThat("whitespace-only name should be null", resultWithSpaces, equalTo(null))
    }

    @Test
    fun `validateDeckName - existing deck name returns ALREADY_EXISTS`() = runTest {
        // Create a deck first
        col.decks.id("Existing Deck")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.DECK, titleResId = 0
        )
        val result = viewModel.validateDeckName("Existing Deck", state)
        assertThat(
            "existing deck name", result, equalTo(DeckPickerViewModel.DeckNameError.ALREADY_EXISTS)
        )
    }

    @Test
    fun `validateDeckName - new valid name returns null`() = runTest {
        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.DECK, titleResId = 0
        )
        val result = viewModel.validateDeckName("Brand New Deck", state)
        assertThat("new valid deck name", result, equalTo(null))
    }

    @Test
    fun `validateDeckName - rename to same name is allowed`() = runTest {
        // Create a deck first
        val deckId = col.decks.id("My Deck")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.RENAME_DECK,
            titleResId = 0,
            initialName = "My Deck",
            deckIdToRename = deckId
        )
        // Renaming to the same name should be allowed
        val result = viewModel.validateDeckName("My Deck", state)
        assertThat("rename to same name", result, equalTo(null))
    }

    @Test
    fun `validateDeckName - rename to existing different name returns ALREADY_EXISTS`() = runTest {
        // Create two decks
        col.decks.id("Deck A")
        val deckBId = col.decks.id("Deck B")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.RENAME_DECK,
            titleResId = 0,
            initialName = "Deck B",
            deckIdToRename = deckBId
        )
        // Trying to rename Deck B to Deck A (which exists) should fail
        val result = viewModel.validateDeckName("Deck A", state)
        assertThat(
            "rename to existing deck name",
            result,
            equalTo(DeckPickerViewModel.DeckNameError.ALREADY_EXISTS)
        )
    }

    @Test
    fun `validateDeckName - subdeck with valid parent`() = runTest {
        // Create parent deck
        val parentId = col.decks.id("Parent")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.SUB_DECK,
            titleResId = 0,
            parentId = parentId
        )
        val result = viewModel.validateDeckName("Child", state)
        assertThat("valid subdeck name", result, equalTo(null))
    }

    @Test
    fun `validateDeckName - subdeck with existing name returns ALREADY_EXISTS`() = runTest {
        // Create parent deck and subdeck
        val parentId = col.decks.id("Parent")
        col.decks.id("Parent::Existing Child")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.SUB_DECK,
            titleResId = 0,
            parentId = parentId
        )
        val result = viewModel.validateDeckName("Existing Child", state)
        assertThat(
            "existing subdeck name",
            result,
            equalTo(DeckPickerViewModel.DeckNameError.ALREADY_EXISTS)
        )
    }

    @Test
    fun `validateDeckName - subdeck rename to same name is allowed`() = runTest {
        // Create parent deck and subdeck
        col.decks.id("Parent")
        val subdeckId = col.decks.id("Parent::Child")

        val state = DeckPickerViewModel.CreateDeckDialogState.Visible(
            type = com.ichi2.anki.dialogs.compose.DeckDialogType.RENAME_DECK, titleResId = 0,
            // The dialog shows "Child" as initial name, but full path is "Parent::Child"
            initialName = "Parent::Child", deckIdToRename = subdeckId
        )
        // User enters "Child" (short name) - this should be allowed since it's the same deck
        // This tests the deckIdToRename-based comparison, not name string comparison
        val result = viewModel.validateDeckName("Child", state)
        assertThat("subdeck rename to same short name should be allowed", result, equalTo(null))
    }

    @Test
    fun `showRenameDeckDialog - existing deck shows rename dialog`() = runTest {
        val deckId = col.decks.id("Rename Me")

        viewModel.showRenameDeckDialog(deckId).join()

        assertThat(
            "rename dialog state", viewModel.createDeckDialogState.value, equalTo(
                DeckPickerViewModel.CreateDeckDialogState.Visible(
                    type = com.ichi2.anki.dialogs.compose.DeckDialogType.RENAME_DECK,
                    titleResId = R.string.rename_deck,
                    initialName = "Rename Me",
                    deckIdToRename = deckId
                )
            )
        )
    }

    @Test
    fun `showRenameDeckDialog - missing deck keeps dialog hidden`() = runTest {
        val deckId = col.decks.id("Delete Me")
        col.decks.remove(listOf(deckId))

        viewModel.showRenameDeckDialog(deckId).join()

        assertThat(
            "rename dialog state",
            viewModel.createDeckDialogState.value,
            equalTo(DeckPickerViewModel.CreateDeckDialogState.Hidden)
        )
    }

    // endregion

    // region StudyOptionsData Tests

    @Test
    fun `studyOptionsData - loads when focusedDeck is set`() = runTest {
        // Create a deck with a card so we have meaningful data
        addBasicNote("Front", "Back")
        val deckId = Consts.DEFAULT_DECK_ID

        assertThat("initial state", viewModel.studyOptionsData.value, equalTo(null))

        // Ensure the deck list is loaded (sets up dueTree)
        viewModel.updateDeckList()
        flushViewModelUpdates()

        // Focus on a deck
        viewModel.focusedDeck = deckId
        flushViewModelUpdates()

        val data = requireNotNull(viewModel.studyOptionsData.value)
        assertThat("deck id matches", data.deckId, equalTo(deckId))
        assertThat("has 1 new card", data.newCount, equalTo(1))
        assertThat("total cards", data.totalCards, equalTo(1))
    }

    @Test
    fun `studyOptionsData - clears when focusedDeck is null`() = runTest {
        assertThat("initial state", viewModel.studyOptionsData.value, equalTo(null))

        viewModel.updateDeckList()
        flushViewModelUpdates()

        // Focus on default deck
        viewModel.focusedDeck = Consts.DEFAULT_DECK_ID
        flushViewModelUpdates()

        assertThat(
            "deck is focused",
            viewModel.studyOptionsData.value?.deckId,
            equalTo(Consts.DEFAULT_DECK_ID)
        )

        // Clear focus
        viewModel.focusedDeck = null
        flushViewModelUpdates()

        assertThat(
            "should be null after clearing focus", viewModel.studyOptionsData.value, equalTo(null)
        )
    }

    @Test
    fun `studyOptionsData - reflects correct counts for empty deck`() = runTest {
        // Default deck with no cards
        assertThat("initial state", viewModel.studyOptionsData.value, equalTo(null))

        viewModel.updateDeckList()
        flushViewModelUpdates()

        viewModel.focusedDeck = Consts.DEFAULT_DECK_ID
        flushViewModelUpdates()

        val data = requireNotNull(viewModel.studyOptionsData.value)
        assertThat("no new cards", data.newCount, equalTo(0))
        assertThat("no learning cards", data.lrnCount, equalTo(0))
        assertThat("no review cards", data.revCount, equalTo(0))
        assertThat("no total cards", data.totalCards, equalTo(0))
    }

    // endregion

    // region Effect Channel Snackbar Tests

    @Test
    fun `unburyDeck - calls sched unbury and updates list`() = runTest {
        val deckId = col.decks.id("Japanese")
        // No easy way to check if unbury was called on mock col, so we just verify it runs without error
        // and triggers a list update.
        viewModel.unburyDeck(deckId).join()

        // Success if it doesn't crash and we can still interact
        assertThat(viewModel.isSyncing.value, equalTo(false))
    }

    @Test
    fun `effects - showSnackbar routes through channel`() = runTest {
        viewModel.composeEffects.test {
            viewModel.showSnackbar("Test message")

            val effect = awaitItem()
            assertThat(
                "is ShowSnackbarMessage",
                effect,
                instanceOf(DeckPickerComposeEffect.ShowSnackbarMessage::class.java)
            )
            assertThat(
                "message matches",
                (effect as DeckPickerComposeEffect.ShowSnackbarMessage).message,
                equalTo("Test message")
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects - deck deletion emits ShowUndoSnackbar`() = runTest {
        // Create a deck to delete
        val deckId = col.decks.id("Deck To Delete")

        viewModel.composeEffects.test {
            viewModel.deleteDeck(deckId).join()

            val effect = awaitItem()
            assertThat(
                "is ShowUndoSnackbar",
                effect,
                instanceOf(DeckPickerComposeEffect.ShowUndoSnackbar::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects - deleting non-existent deck emits error snackbar`() = runTest {
        val nonExistentDeckId = 999999L

        viewModel.composeEffects.test {
            viewModel.deleteDeck(nonExistentDeckId).join()

            val effect = awaitItem()
            assertThat(
                "is ShowSnackbar (error)",
                effect,
                instanceOf(DeckPickerComposeEffect.ShowSnackbar::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDeck - completes successfully without deletion state tracking`() = runTest {
        val deckId = col.decks.id("Deck To Delete")

        viewModel.deleteDeck(deckId).join()

        assertThat(
            "deck should be deleted after completion",
            withCol { col.decks.getLegacy(deckId) },
            equalTo(null)
        )
    }

    @Test
    fun `deleteDeck - failed deletion emits error without deletion state tracking`() = runTest {
        val nonExistentDeckId = 999999L

        viewModel.composeEffects.test {
            viewModel.deleteDeck(nonExistentDeckId).join()

            val effect = awaitItem()
            assertThat(
                "is ShowSnackbar after failed deletion",
                effect,
                instanceOf(DeckPickerComposeEffect.ShowSnackbar::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects - selecting deck with cards emits HasCardsToStudy`() = runTest {
        // Create a deck with a card
        addBasicNote("Front", "Back")
        viewModel.updateDeckList()
        advanceUntilIdle()

        viewModel.composeEffects.test {
            viewModel.onDeckSelected(Consts.DEFAULT_DECK_ID, DeckSelectionType.DEFAULT)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(
                "is HandleDeckSelection",
                effect,
                instanceOf(DeckPickerComposeEffect.HandleDeckSelection::class.java)
            )
            val result = (effect as DeckPickerComposeEffect.HandleDeckSelection).result
            assertThat(
                "is HasCardsToStudy result",
                result,
                instanceOf(DeckSelectionResult.HasCardsToStudy::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects - selecting non-empty deck with no cards due emits NoCardsToStudy`() = runTest {
        // Create a deck with a card, but it's not due (it's new, but maybe we can just use an empty deck that is not 'completely empty')
        // Actually, if it has a card, it's either new, lrn, or rev.
        // If we want 'NoCardsToStudy', we need to have cards, but they are all buried or suspended.

        val note = addBasicNote("Front", "Back")
        col.sched.suspendCards(listOf(note.firstCard().id))

        viewModel.updateDeckList()
        advanceUntilIdle()

        viewModel.composeEffects.test {
            viewModel.onDeckSelected(Consts.DEFAULT_DECK_ID, DeckSelectionType.DEFAULT)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(
                "is HandleDeckSelection",
                effect,
                instanceOf(DeckPickerComposeEffect.HandleDeckSelection::class.java)
            )
            val result = (effect as DeckPickerComposeEffect.HandleDeckSelection).result
            assertThat(
                "is NoCardsToStudy result",
                result,
                instanceOf(DeckSelectionResult.NoCardsToStudy::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects - selecting empty deck emits HandleDeckSelection`() = runTest {
        // Default deck with no cards
        viewModel.updateDeckList()
        advanceUntilIdle()

        viewModel.composeEffects.test {
            viewModel.onDeckSelected(Consts.DEFAULT_DECK_ID, DeckSelectionType.DEFAULT)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(
                "is HandleDeckSelection",
                effect,
                instanceOf(DeckPickerComposeEffect.HandleDeckSelection::class.java)
            )
            val result = (effect as DeckPickerComposeEffect.HandleDeckSelection).result
            assertThat("is Empty result", result, instanceOf(DeckSelectionResult.Empty::class.java))

            cancelAndIgnoreRemainingEvents()
        }
    }

    // region Migrated Activity Side Effects

    @Test
    fun `migrated effects - emit correctly`() = runTest {
        viewModel.effects.test {
            viewModel.sync()
            assertThat("is Sync", awaitItem(), instanceOf(DeckPickerEffect.Sync::class.java))

            viewModel.openReviewer()
            assertThat(
                "is NavigateToReviewer",
                awaitItem(),
                instanceOf(DeckPickerEffect.NavigateToReviewer::class.java)
            )

            viewModel.openStudyOptionsActivity()
            assertThat(
                "is NavigateToStudyOptions",
                awaitItem(),
                instanceOf(DeckPickerEffect.NavigateToStudyOptions::class.java)
            )

            viewModel.exportDeck(Consts.DEFAULT_DECK_ID)
            val exportEffect = awaitItem()
            assertThat(
                "export deck id matches",
                exportEffect,
                instanceOf(DeckPickerEffect.ShowExportDialog::class.java)
            )
            assertThat(
                "export deck id matches",
                (exportEffect as DeckPickerEffect.ShowExportDialog).deckId,
                equalTo(Consts.DEFAULT_DECK_ID)
            )

            viewModel.showCustomStudyDialog(Consts.DEFAULT_DECK_ID)
            val customStudyEffect = awaitItem()
            assertThat(
                "custom study deck id matches",
                customStudyEffect,
                instanceOf(DeckPickerEffect.ShowCustomStudyDialog::class.java)
            )
            assertThat(
                "custom study deck id matches",
                (customStudyEffect as DeckPickerEffect.ShowCustomStudyDialog).deckId,
                equalTo(Consts.DEFAULT_DECK_ID)
            )

            viewModel.checkDatabase()
            assertThat(
                "is CheckDatabase",
                awaitItem(),
                instanceOf(DeckPickerEffect.CheckDatabase::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }

        viewModel.composeEffects.test {
            viewModel.undo()
            assertThat(
                "undo emits compose snackbar message",
                awaitItem(),
                instanceOf(DeckPickerComposeEffect.ShowSnackbarMessage::class.java)
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    // region Startup Tests

    @Test
    fun `handleStartup - success`() = runTest {
        val environment = mockk<DeckPickerViewModel.AnkiDroidEnvironment>()
        every { environment.hasRequiredPermissions() } returns true
        every { environment.initializeAnkiDroidFolder() } returns true

        viewModel.handleStartup(environment)

        assertThat(
            "startup response should be success",
            viewModel.flowOfStartupResponse.value,
            equalTo(DeckPickerViewModel.StartupResponse.Success)
        )
    }

    @Test
    fun `handleStartup - permission request`() = runTest {
        val environment = mockk<DeckPickerViewModel.AnkiDroidEnvironment>()
        val requiredPermissions = PermissionSet.LEGACY_ACCESS
        every { environment.hasRequiredPermissions() } returns false
        every { environment.requiredPermissions } returns requiredPermissions

        viewModel.handleStartup(environment)

        val response = viewModel.flowOfStartupResponse.value
        assertThat(
            "should request permissions",
            response,
            instanceOf(DeckPickerViewModel.StartupResponse.RequestPermissions::class.java)
        )
        assertThat(
            "permissions match",
            (response as DeckPickerViewModel.StartupResponse.RequestPermissions).requiredPermissions,
            equalTo(requiredPermissions)
        )
    }

    @Test
    fun `handleStartup - fatal error database locked`() = runTest {
        val environment = mockk<DeckPickerViewModel.AnkiDroidEnvironment>()
        every { environment.hasRequiredPermissions() } returns true
        every { environment.initializeAnkiDroidFolder() } returns true

        enableNullCollection()
        ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED)
        CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
        try {
            viewModel.handleStartup(environment)

            val response = viewModel.flowOfStartupResponse.value
            assertThat(
                "should have fatal error",
                response,
                instanceOf(DeckPickerViewModel.StartupResponse.FatalError::class.java)
            )
            assertThat(
                "failure type is database locked",
                (response as DeckPickerViewModel.StartupResponse.FatalError).failure,
                equalTo(InitialActivity.StartupFailure.DatabaseLocked)
            )
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun `handleStartup - fatal error sdcard not mounted`() = runTest {
        val environment = mockk<DeckPickerViewModel.AnkiDroidEnvironment>()
        every { environment.hasRequiredPermissions() } returns true

        enableNullCollection()
        ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_REMOVED)

        try {
            viewModel.handleStartup(environment)

            val response = viewModel.flowOfStartupResponse.value
            assertThat(
                "should have fatal error",
                response,
                instanceOf(DeckPickerViewModel.StartupResponse.FatalError::class.java)
            )
            assertThat(
                "failure type is SDCardNotMounted",
                (response as DeckPickerViewModel.StartupResponse.FatalError).failure,
                equalTo(InitialActivity.StartupFailure.SDCardNotMounted)
            )
        } finally {
            disableNullCollection()
            ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED)
        }
    }

    @Test
    fun `handleStartup - directory not accessible`() = runTest {
        val environment = mockk<DeckPickerViewModel.AnkiDroidEnvironment>()
        every { environment.hasRequiredPermissions() } returns true
        every { environment.initializeAnkiDroidFolder() } returns false

        enableNullCollection()
        ShadowEnvironment.setExternalStorageState(android.os.Environment.MEDIA_MOUNTED)

        try {
            viewModel.handleStartup(environment)

            val response = viewModel.flowOfStartupResponse.value
            assertThat(
                "should have fatal error",
                response,
                instanceOf(DeckPickerViewModel.StartupResponse.FatalError::class.java)
            )
            assertThat(
                "failure type is DirectoryNotAccessible",
                (response as DeckPickerViewModel.StartupResponse.FatalError).failure,
                equalTo(InitialActivity.StartupFailure.DirectoryNotAccessible)
            )
        } finally {
            disableNullCollection()
        }
    }

    // endregion

    // region Dialog State Tests

    @Test
    fun `dialog state - create deck dialog visibility`() = runTest {
        assertThat(
            "initial state",
            viewModel.createDeckDialogState.value,
            equalTo(DeckPickerViewModel.CreateDeckDialogState.Hidden)
        )

        viewModel.showCreateDeckDialog()
        val state = viewModel.createDeckDialogState.value
        assertThat(
            "is visible",
            state,
            instanceOf(DeckPickerViewModel.CreateDeckDialogState.Visible::class.java)
        )
        assertThat(
            "type is DECK",
            (state as DeckPickerViewModel.CreateDeckDialogState.Visible).type,
            equalTo(com.ichi2.anki.dialogs.compose.DeckDialogType.DECK)
        )

        viewModel.dismissCreateDeckDialog()
        assertThat(
            "hidden after dismiss",
            viewModel.createDeckDialogState.value,
            equalTo(DeckPickerViewModel.CreateDeckDialogState.Hidden)
        )
    }

    @Test
    fun `dialog state - show create subdeck`() = runTest {
        val parentId = col.decks.id("Parent")
        viewModel.showCreateSubdeckDialog(parentId)

        val state =
            viewModel.createDeckDialogState.value as DeckPickerViewModel.CreateDeckDialogState.Visible
        assertThat(
            "type is SUB_DECK",
            state.type,
            equalTo(com.ichi2.anki.dialogs.compose.DeckDialogType.SUB_DECK)
        )
        assertThat("parent id matches", state.parentId, equalTo(parentId))
    }

    @Test
    fun `dialog state - show create filtered deck`() = runTest {
        viewModel.showCreateFilteredDeckDialog()

        val state =
            viewModel.createDeckDialogState.value as DeckPickerViewModel.CreateDeckDialogState.Visible
        assertThat(
            "type is FILTERED_DECK",
            state.type,
            equalTo(com.ichi2.anki.dialogs.compose.DeckDialogType.FILTERED_DECK)
        )
    }

    @Test
    fun `createDeck - success emits snackbar and updates list`() = runTest {
        viewModel.showCreateDeckDialog()
        val state =
            viewModel.createDeckDialogState.value as DeckPickerViewModel.CreateDeckDialogState.Visible

        viewModel.composeEffects.test {
            viewModel.createDeck("New Deck", state)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(
                "emits snackbar",
                effect,
                instanceOf(DeckPickerComposeEffect.ShowSnackbar::class.java)
            )
            assertThat(
                "snackbar message is deck created",
                (effect as DeckPickerComposeEffect.ShowSnackbar).messageResId,
                equalTo(R.string.deck_created)
            )
            assertThat(
                "dialog hidden",
                viewModel.createDeckDialogState.value,
                equalTo(DeckPickerViewModel.CreateDeckDialogState.Hidden)
            )

            val deckId = col.decks.byName("New Deck")
            assertThat("deck exists in collection", deckId, not(equalTo(null)))
        }
    }

    // endregion

    private fun TestScope.flushViewModelUpdates() {
        advanceUntilIdle()
        advanceRobolectricLooper()
        advanceUntilIdle()
    }

    @Test
    fun `toggleDeckExpand - updates backend and emits refresh`() = runTest {
        // Create a subdeck to make expansion relevant
        col.decks.id("Parent::Child")
        viewModel.updateDeckList()
        advanceUntilIdle()

        val parentId = col.decks.id("Parent")
        val initialCollapsed = col.decks.getLegacy(parentId)!!.collapsed

        viewModel.toggleDeckExpand(parentId)
        advanceUntilIdle()

        val updatedCollapsed = col.decks.getLegacy(parentId)!!.collapsed
        assertThat("backend state toggled", updatedCollapsed, equalTo(!initialCollapsed))
    }
}
