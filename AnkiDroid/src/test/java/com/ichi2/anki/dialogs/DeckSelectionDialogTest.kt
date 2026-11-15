// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>

package com.ichi2.anki.dialogs

import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.SelectableDeck
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckSelectionDialogTest : RobolectricTest() {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "sub-deck3"

        val deck = SelectableDeck.Deck(1234, input)
        val actual: String = deck.getDisplayName(targetContext)

        assertThat(actual, Matchers.equalTo(expected))
    }

    @Test
    fun testDialogCreation() {
        val decks: List<SelectableDeck> = listOf(SelectableDeck.Deck(5L, "deck"))
        val dialogTitle = "Select Deck"
        val summaryMessage = "Choose a deck from the list"
        val keepRestoreDefaultButton = true

        val dialog = DeckSelectionDialog.newInstance(dialogTitle, summaryMessage, keepRestoreDefaultButton, decks)
        assertNotNull(dialog)
        assertEquals(dialogTitle, dialog.arguments?.getString("title"))
        assertEquals(summaryMessage, dialog.arguments?.getString("summaryMessage"))
    }
}
