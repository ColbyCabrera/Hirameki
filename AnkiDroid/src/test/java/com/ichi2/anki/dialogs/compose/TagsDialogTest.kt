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

package com.ichi2.anki.dialogs.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h1280dp")
class TagsDialogTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun addTagChipHiddenWhenOnAddTagIsNull() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val addTagDescription = context.getString(R.string.add_tag)

        composeTestRule.setContent {
            AnkiDroidTheme {
                TagsDialog(
                    onDismissRequest = {},
                    onConfirm = { _, _ -> },
                    allTags = TagsState.Loaded(listOf("tag1", "tag2")),
                    initialSelection = emptySet(),
                    title = "Test",
                    confirmButtonText = "OK",
                    onAddTag = null,
                )
            }
        }

        composeTestRule.onNodeWithText("tag1").assertIsDisplayed()
        composeTestRule.onNodeWithText("tag2").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(addTagDescription).assertDoesNotExist()
    }

    @Test
    fun maxSelectionRestrictsCheckingBeyondLimitWhilePermittingUnchecking() {
        var maxSelectionReachedCalled = false
        var confirmedTags = emptySet<String>()

        composeTestRule.setContent {
            AnkiDroidTheme {
                TagsDialog(
                    onDismissRequest = {},
                    onConfirm = { checked, _ -> confirmedTags = checked },
                    allTags = TagsState.Loaded(listOf("tag1", "tag2", "tag3")),
                    initialSelection = setOf("tag1"),
                    title = "Test",
                    confirmButtonText = "OK",
                    maxSelection = 2,
                    onMaxSelectionReached = { maxSelectionReachedCalled = true },
                    onAddTag = null,
                )
            }
        }

        // tag1 is initially checked. Checking tag2 reaches the limit of 2.
        composeTestRule.onNodeWithText("tag2").performClick()

        // Checking tag3 should be rejected because maxSelection = 2.
        composeTestRule.onNodeWithText("tag3").performClick()
        assertThat("onMaxSelectionReached was called", maxSelectionReachedCalled, equalTo(true))

        // Unchecking tag1 should be allowed even when at the limit.
        composeTestRule.onNodeWithText("tag1").performClick()

        // Now confirm and verify only tag2 was selected.
        composeTestRule.onNodeWithText("OK").performClick()
        assertThat(confirmedTags, equalTo(setOf("tag2")))
    }
}
