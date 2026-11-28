// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <62114487+david-allison@users.noreply.github.com>

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.deck_config.UpdateDeckConfigsMode
import anki.deck_config.copy
import anki.deck_config.updateDeckConfigsRequest
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Consts
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckOptionsRequestOrderTest : RobolectricTest() {
    @Test
    fun `queued close waits for the preceding deck options save`() = assertCloseWaitsForSave(stopped = true)

    @Test
    fun `close waits for a save while deck options is visible`() = assertCloseWaitsForSave(stopped = false)

    @Test
    fun `close waits for Save and Optimize`() =
        assertCloseWaitsForSave(
            stopped = false,
            mode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_COMPUTE_ALL_PARAMS,
        )

    @Test
    fun `a failed save releases the queued close`() =
        runTest {
            startDeckOptionsController().use { controller ->
                val activity = controller.get()
                val page = activity.fragment as DeckOptions

                // Invalid protobuf: the save must fail without leaving the close locked out.
                withPendingSaveAndClose(page, byteArrayOf(0)) { save, close ->
                    assertFalse(close.isCompleted)
                    assertFalse(activity.isFinishing)
                    testScheduler.runCurrent()
                    assertTrue(save.await().isFailure)
                    close.await()
                    testScheduler.runCurrent()
                    advanceRobolectricLooper()
                    assertTrue(activity.isFinishing)
                }
            }
        }

    private fun assertCloseWaitsForSave(
        stopped: Boolean,
        mode: UpdateDeckConfigsMode = UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL,
    ) = runTest {
        startDeckOptionsController().use { controller ->
            val activity = controller.get()
            val page = activity.fragment as DeckOptions
            val input = deckOptionsSaveRequest(mode, newCardsPerDay = 42)
            if (stopped) controller.pause().stop()

            withPendingSaveAndClose(page, input) { save, close ->
                assertFalse(activity.isFinishing)
                assertFalse(close.isCompleted)

                // Other page requests must still be handled while the save is pending.
                page.handlePostRequest(PostRequestUri("/_anki/deckOptionsReady"), byteArrayOf())
                assertFalse(save.isCompleted)

                if (stopped) controller.start().resume().visible()
                testScheduler.runCurrent()
                save.await().getOrThrow()
                close.await()
                testScheduler.runCurrent()
                advanceRobolectricLooper()

                val newPerDay = withCol { decks.configDictForDeckId(Consts.DEFAULT_DECK_ID).new.perDay }
                assertEquals(42, newPerDay)
                assertTrue(activity.isFinishing)
            }
        }
    }

    private fun startDeckOptionsController(): ActivityController<SingleFragmentActivity> {
        val intent = DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID)
        return Robolectric.buildActivity(SingleFragmentActivity::class.java, intent).apply {
            setup()
            advanceRobolectricLooper()
        }
    }

    private fun deckOptionsSaveRequest(
        mode: UpdateDeckConfigsMode,
        @Suppress("SameParameterValue") newCardsPerDay: Int,
    ): ByteArray {
        val data = col.backend.getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
        val preset =
            data.allConfigList.single().config.copy {
                config = config.copy { newPerDay = newCardsPerDay }
            }
        return updateDeckConfigsRequest {
            targetDeckId = Consts.DEFAULT_DECK_ID
            this.mode = mode
            fsrs = true
            configs += preset
        }.toByteArray()
    }

    /**
     * Simulate a close request arriving while a save is waiting for collection access.
     *
     * Provides 'save' and 'close' to the receiver so deferred work can be started.
     *
     * Fails if the close request completes before the save.
     */
    private suspend fun TestScope.withPendingSaveAndClose(
        page: DeckOptions,
        input: ByteArray,
        assertions: suspend (save: Deferred<Result<ByteArray>>, close: Deferred<Unit>) -> Unit,
    ) {
        val previousIoDispatcher = ioDispatcher
        ioDispatcher = StandardTestDispatcher(testScheduler)
        try {
            coroutineScope {
                val save =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { page.handlePostRequest(PostRequestUri("/_anki/updateDeckConfigs"), input) }
                    }
                val close =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        page.handlePostRequest(PostRequestUri("/_anki/deckOptionsRequireClose"), byteArrayOf())
                        assertTrue(save.isCompleted, "Close replied before the preceding save")
                    }
                assertions(save, close)
            }
        } finally {
            ioDispatcher = previousIoDispatcher
        }
    }
}
