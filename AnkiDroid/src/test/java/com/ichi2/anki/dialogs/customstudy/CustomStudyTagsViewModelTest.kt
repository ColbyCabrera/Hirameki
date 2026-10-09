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
            col.updateNote(note)

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
    fun `fragment launches without crashing`() {
        com.ichi2.testutils.AnkiFragmentScenario
            .launch(
                CustomStudyTagsDialogFragment::class.java,
                android.os.Bundle().apply {
                    putLong(ARG_DECK_ID, DEFAULT_DECK_ID)
                },
            ).use { scenario ->
                scenario.onFragment { fragment ->
                    assertThat(fragment.dialog, org.hamcrest.Matchers.notNullValue())
                }
            }
    }
}
