// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MissingUserActionDialogTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsMessageAndTriggersOkAndHelpCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedTitle = context.getString(R.string.vague_error)
        val expectedMessage = context.getString(R.string.missing_user_action_dialog_message, "4")
        val okLabel = context.getString(R.string.dialog_ok)
        val helpLabel = context.getString(R.string.help)

        var dismissed = false
        var helpClicked = false

        composeTestRule.setContent {
            AnkiDroidTheme {
                MissingUserActionDialog(
                    actionNumber = "4",
                    onDismissRequest = { dismissed = true },
                    onHelpClick = { helpClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedMessage).assertIsDisplayed()

        composeTestRule.onNodeWithText(helpLabel).performClick()
        assertTrue(helpClicked)

        composeTestRule.onNodeWithText(okLabel).performClick()
        assertTrue(dismissed)
    }
}
