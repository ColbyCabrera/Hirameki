// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <62114487+david-allison@users.noreply.github.com>

package com.ichi2.anki.browser

import android.content.Intent
import android.view.WindowManager
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserPreviewTest : JvmTest() {
    private val hostControllers = mutableListOf<ActivityController<EmptyAnkiActivity>>()

    private fun launchHostActivity(): EmptyAnkiActivity =
        Robolectric
            .buildActivity(EmptyAnkiActivity::class.java)
            .setup()
            .also(hostControllers::add)
            .get()

    @After
    fun destroyHostActivities() {
        hostControllers.forEach { it.destroy() }
        hostControllers.clear()
    }

    @Test
    fun `firstCardIdsOfNotes returns empty list for empty input`() {
        assertEquals(emptyList(), col.firstCardIdsOfNotes(emptyList()))
    }

    @Test
    fun `firstCardIdsOfNotes returns lowest ordinal card per note preserving order`() {
        val note1 = addBasicAndReversedNote("Front 1", "Back 1")
        val note2 = addBasicAndReversedNote("Front 2", "Back 2")
        val note3 = addBasicNote("Front 3", "Back 3")

        // Remove ordinal 0 from note2 so its lowest remaining ordinal is 1.
        val note2Cards = note2.cards(col).sortedBy { it.ord }
        col.removeCardsAndOrphanedNotes(listOf(note2Cards[0].id))

        val expected1 = note1.cards(col).minBy { it.ord }.id
        val expected2 = note2Cards[1].id
        val expected3 = note3.firstCard(col).id

        val requestedOrder = listOf(note3.id, note2.id, note1.id)
        assertEquals(
            listOf(expected3, expected2, expected1),
            col.firstCardIdsOfNotes(requestedOrder),
        )
    }

    @Test
    fun `firstCardIdsOfNotes throws when note has no cards`() {
        assertFailsWith<NoSuchElementException> {
            col.firstCardIdsOfNotes(listOf(999_999L))
        }
    }

    @Test
    fun `queryPreviewIntentData in notes mode returns one lowest-ordinal card per note`() =
        runTest(UnconfinedTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                val note1 = addBasicAndReversedNote("A", "B")
                val note2 = addBasicAndReversedNote("C", "D")
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                val previewData = viewModel.queryPreviewIntentData()
                val previewIds = previewData.idsFile.getIds()
                val expectedByNote =
                    mapOf(
                        note1.id to note1.cards(col).minBy { it.ord }.id,
                        note2.id to note2.cards(col).minBy { it.ord }.id,
                    )
                val expectedIds = viewModel.cards.map { expectedByNote.getValue(it.cardOrNoteId) }
                assertEquals(expectedIds, previewIds)
            } finally {
                ioDispatcher = originalDispatcher
            }
        }

    @Test
    fun `onPreview blocks double taps while preview preparation is in flight`() =
        runTest(StandardTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                addBasicAndReversedNote("Front", "Back")
                val activity = launchHostActivity()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                val launchedIntents = mutableListOf<Intent>()
                val actionHandler =
                    CardBrowserActionHandler(
                        activity = activity,
                        viewModel = viewModel,
                        launchEditCard = {},
                        launchAddNote = {},
                        launchPreview = { launchedIntents.add(it) },
                    )

                // Pause IO scheduling so the first preview coroutine stays active while the second tap arrives.
                ioDispatcher = StandardTestDispatcher(testScheduler)
                val first = assertNotNull(actionHandler.onPreview())
                assertTrue(first.isActive, "Preview preparation should suspend while waiting on IO")
                assertTrue(
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0,
                    "Window touches should be blocked while preview is preparing",
                )
                val second = actionHandler.onPreview()
                assertSame(first, second, "Second tap while preview is preparing must reuse the active job")

                testScheduler.advanceUntilIdle()
                first.join()
                ShadowLooper.idleMainLooper()

                assertFalse(
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0,
                    "Window touches should be restored after preview preparation completes",
                )
                assertEquals(1, launchedIntents.size, "Double-tap must only launch the previewer once")
            } finally {
                ioDispatcher = originalDispatcher
                ShadowLooper.idleMainLooper()
            }
        }

    @Test
    fun `cancelled preview preparation allows a subsequent preview to succeed`() =
        runTest(StandardTestDispatcher()) {
            val originalDispatcher = ioDispatcher
            ioDispatcher = UnconfinedTestDispatcher(testScheduler)
            try {
                addBasicNote("Front", "Back")
                val activity = launchHostActivity()
                val viewModel = createTestViewModel(CardsOrNotes.NOTES)

                val launchedIntents = mutableListOf<Intent>()
                val actionHandler =
                    CardBrowserActionHandler(
                        activity = activity,
                        viewModel = viewModel,
                        launchEditCard = {},
                        launchAddNote = {},
                        launchPreview = { launchedIntents.add(it) },
                    )

                ioDispatcher = StandardTestDispatcher(testScheduler)
                val first = assertNotNull(actionHandler.onPreview())
                assertTrue(first.isActive)
                first.cancelAndJoin()
                assertTrue(first.isCancelled)
                assertEquals(0, launchedIntents.size)

                val retry = assertNotNull(actionHandler.onPreview())
                testScheduler.advanceUntilIdle()
                retry.join()
                ShadowLooper.idleMainLooper()

                assertEquals(1, launchedIntents.size)
            } finally {
                ioDispatcher = originalDispatcher
                ShadowLooper.idleMainLooper()
            }
        }

    private suspend fun createTestViewModel(mode: CardsOrNotes): CardBrowserViewModel {
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
