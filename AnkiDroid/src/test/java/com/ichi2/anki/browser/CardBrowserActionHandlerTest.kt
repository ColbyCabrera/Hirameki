/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.pages.PageFragment
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserActionHandlerTest : JvmTest() {

    @Test
    fun `openNoteEditorForRow launches NoteEditor with CardId in CARDS mode`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val note = addBasicAndReversedNote()
                val noteCardIds = note.cardIds(col)

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.CARDS)

                var launchedIntent: Intent? = null
                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = { launchedIntent = it },
                    launchAddNote = {},
                    launchPreview = {},
                )

                val firstRow = viewModel.cards.first()
                actionHandler.openNoteEditorForRow(firstRow).join()
                ShadowLooper.idleMainLooper()

                assertNotNull(launchedIntent)
                val launchedCardId =
                    launchedIntent.getLongExtra(NoteEditorActivity.EXTRA_CARD_ID, 0)
                assertThat(launchedCardId > 0, equalTo(true))
                assertTrue(launchedCardId in noteCardIds)
                assertThat(launchedCardId, equalTo(firstRow.cardOrNoteId))
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openNoteEditorForRow resolves NoteId to CardId in NOTES mode - Issue 137`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val note = addBasicAndReversedNote()
                val noteCardIds = note.cardIds(col)

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                var launchedIntent: Intent? = null
                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = { launchedIntent = it },
                    launchAddNote = {},
                    launchPreview = {},
                )

                val firstRow = viewModel.cards.first()
                assertThat(
                    "In notes mode row ID is NoteId",
                    firstRow.cardOrNoteId,
                    equalTo(note.id)
                )

                actionHandler.openNoteEditorForRow(firstRow).join()
                ShadowLooper.idleMainLooper()

                assertNotNull(launchedIntent)
                val launchedCardId =
                    launchedIntent.getLongExtra(NoteEditorActivity.EXTRA_CARD_ID, 0)
                assertThat("Launched CardId must not be 0", launchedCardId > 0, equalTo(true))
                assertTrue(launchedCardId in noteCardIds)
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openNoteEditorForSelectedRow opens NoteEditor for selection without relying on currentCardId`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val note = addBasicNote("Front", "Back")
                val expectedCardId = note.firstCard().id

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.CARDS)

                // Select the single row via selectAll (similar to user action in crash report)
                viewModel.toggleSelectAllOrNone()
                assertThat(viewModel.selectedRows.size, equalTo(1))

                var launchedIntent: Intent? = null
                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = { launchedIntent = it },
                    launchAddNote = {},
                    launchPreview = {},
                )

                actionHandler.openNoteEditorForSelectedRow()?.join()
                ShadowLooper.idleMainLooper()

                assertNotNull(launchedIntent)
                val launchedCardId =
                    launchedIntent.getLongExtra(NoteEditorActivity.EXTRA_CARD_ID, 0)
                assertThat(
                    "Launched Card ID must be valid and non-zero",
                    launchedCardId,
                    equalTo(expectedCardId)
                )
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openNoteEditorForSelectedRow does nothing if no rows selected`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                addBasicNote("Front", "Back")

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.CARDS)

                var launchedIntent: Intent? = null
                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = { launchedIntent = it },
                    launchAddNote = {},
                    launchPreview = {},
                )

                actionHandler.openNoteEditorForSelectedRow()?.join()
                ShadowLooper.idleMainLooper()
                assertThat(
                    "No intent should be launched when selection is empty",
                    launchedIntent,
                    nullValue()
                )
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openCardInfoForSelectedRow resolves Note to CardId in NOTES mode`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val note = addBasicAndReversedNote()
                val noteCardIds = note.cardIds(col)

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                // Select the note row
                viewModel.toggleSelectAllOrNone()
                assertThat(viewModel.selectedRows.size, equalTo(1))

                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = {},
                    launchAddNote = {},
                    launchPreview = {},
                )

                val shadowActivity = org.robolectric.Shadows.shadowOf(activity)
                shadowActivity.clearNextStartedActivities()

                actionHandler.openCardInfoForSelectedRow()?.join()
                ShadowLooper.idleMainLooper()

                val startedIntent = shadowActivity.nextStartedActivity
                assertNotNull(startedIntent)
                val fragmentArgs =
                    startedIntent.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)
                assertNotNull(fragmentArgs)
                val path = fragmentArgs.getString(PageFragment.PATH_ARG_KEY)
                assertNotNull(path)
                assertTrue(noteCardIds.any { path == "card-info/$it" })
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openCardInfoForSelectedRow does nothing if no rows selected`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                addBasicNote("Front", "Back")

                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val shadowActivity = org.robolectric.Shadows.shadowOf(activity)
                shadowActivity.clearNextStartedActivities()
                val viewModel = createTestViewModel(CardsOrNotes.CARDS)

                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = {},
                    launchAddNote = {},
                    launchPreview = {},
                )

                actionHandler.openCardInfoForSelectedRow()?.join()
                ShadowLooper.idleMainLooper()

                val startedIntent = shadowActivity.nextStartedActivity
                assertThat("No activity should be started when selection is empty", startedIntent, nullValue())
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openNoteEditorForRow emits snackbar when note has no cards`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                var launchedIntent: Intent? = null
                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = { launchedIntent = it },
                    launchAddNote = {},
                    launchPreview = {},
                )

                // Non-existent / 0-card Note ID (e.g. 999999L)
                val invalidNoteId = CardOrNoteId(999999L)
                viewModel.flowOfSnackbarString.test {
                    actionHandler.openNoteEditorForRow(invalidNoteId).join()
                    ShadowLooper.idleMainLooper()

                    assertThat("No editor should launch for empty/invalid note", launchedIntent, nullValue())
                    assertThat(
                        "Snackbar message should indicate no note to edit",
                        awaitItem().message,
                        equalTo(activity.getString(R.string.no_note_to_edit))
                    )
                }
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `openCardInfoForSelectedRow emits snackbar when note has no cards`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val activity = Robolectric.buildActivity(DeckPicker::class.java).setup().get()
                val shadowActivity = org.robolectric.Shadows.shadowOf(activity)
                shadowActivity.clearNextStartedActivities()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                // Manually select an invalid NoteId to simulate a corrupted 0-card note
                val invalidNoteId = CardOrNoteId(999999L)
                viewModel.toggleRowSelection(CardBrowserViewModel.RowSelection(invalidNoteId, 0)).join()
                assertThat(viewModel.selectedRows.size, equalTo(1))

                val actionHandler = CardBrowserActionHandler(
                    activity = activity,
                    viewModel = viewModel,
                    launchEditCard = {},
                    launchAddNote = {},
                    launchPreview = {},
                )

                viewModel.flowOfSnackbarString.test {
                    actionHandler.openCardInfoForSelectedRow()?.join()
                    ShadowLooper.idleMainLooper()

                    val startedIntent = shadowActivity.nextStartedActivity
                    assertThat("No card info should open for 0-card note", startedIntent, nullValue())
                    assertThat(
                        "Snackbar message should be emitted for 0-card note",
                        awaitItem().message,
                        equalTo(activity.getString(R.string.no_note_to_edit))
                    )
                }
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    private suspend fun createTestViewModel(mode: CardsOrNotes = CardsOrNotes.CARDS): CardBrowserViewModel {
        if (mode == CardsOrNotes.NOTES) {
            mode.saveToCollection(col)
        }
        val cache = createTransientDirectory()
        return CardBrowserViewModel(
            lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
            cacheDir = cache,
            options = null,
            preferences = AnkiDroidApp.sharedPreferencesProvider,
            isFragmented = false,
            manualInit = false,
            savedStateHandle = SavedStateHandle(),
        ).apply {
            flowOfInitCompleted.first { it }
            searchJob?.join()
        }
    }
}
