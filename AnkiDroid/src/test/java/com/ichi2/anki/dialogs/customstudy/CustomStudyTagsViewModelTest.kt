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

package com.ichi2.anki.dialogs.customstudy

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.dialogs.compose.TagsState
import com.ichi2.anki.dialogs.customstudy.CustomStudyTagsDialogFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CustomStudyTagsViewModelTest : JvmTest() {
    @Test
    fun `deck tags are loaded alphabetically and exclude tags tag`() =
        runTest {
            val note = addBasicNote()
            note.setTagsFromStr(col, "zebra alpha tags beta")
            assertThat("the tags were written to the collection", col.updateNote(note).note, equalTo(true))

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val viewModel =
                CustomStudyTagsViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(ARG_DECK_ID to DEFAULT_DECK_ID)),
                    ioDispatcher = dispatcher,
                )

            viewModel.tagsState.test {
                val state = expectMostRecentItem()
                assertThat(state, instanceOf(TagsState.Loaded::class.java))
                val loaded = state as TagsState.Loaded
                assertThat(loaded.tags, equalTo(listOf("alpha", "beta", "zebra")))
            }
        }

    @Test
    fun `empty deck yields empty tags list`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val viewModel =
                CustomStudyTagsViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(ARG_DECK_ID to DEFAULT_DECK_ID)),
                    ioDispatcher = dispatcher,
                )

            viewModel.tagsState.test {
                val state = expectMostRecentItem()
                assertThat(state, instanceOf(TagsState.Loaded::class.java))
                val loaded = state as TagsState.Loaded
                assertThat(loaded.tags, equalTo(emptyList()))
            }
        }

    @Test
    fun `retry re-runs the query`() =
        runTest {
            val note = addBasicNote()
            note.setTagsFromStr(col, "first-tag")
            assertThat("the tags were written to the collection", col.updateNote(note).note, equalTo(true))

            val viewModel =
                CustomStudyTagsViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(ARG_DECK_ID to DEFAULT_DECK_ID)),
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            viewModel.tagsState.test {
                val initial = expectMostRecentItem() as TagsState.Loaded
                assertThat(initial.tags, equalTo(listOf("first-tag")))

                // Change the deck's tags so a genuine re-query has to produce a different
                // answer. StateFlow drops an emission equal to the current value, so re-reading
                // the same list would be invisible here and would prove nothing.
                note.setTagsFromStr(col, "second-tag")
                assertThat("the new tags were written", col.updateNote(note).note, equalTo(true))

                viewModel.retry()

                val reloaded = expectMostRecentItem() as TagsState.Loaded
                assertThat(reloaded.tags, equalTo(listOf("second-tag")))
            }
        }
}
