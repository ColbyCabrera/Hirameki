// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.MetaDB
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for whiteboard persistence in [MetaDB].
 *
 * These tests verify the data layer behavior for whiteboard state and visibility
 * storage, which [WhiteboardController] depends on. Testing the controller directly
 * would require mocking Android Views and lifecycle components.
 */
@RunWith(AndroidJUnit4::class)
class WhiteboardControllerTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        MetaDB.resetDB(targetContext)
    }

    // region MetaDB Whiteboard State Tests

    @Test
    fun whiteboardStateIsDisabledByDefault() {
        // When no state is stored, whiteboard should be disabled
        val state = MetaDB.getWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard is disabled by default", state, equalTo(false))
    }

    @Test
    fun whiteboardStateCanBeEnabled() {
        // Store enabled state
        MetaDB.storeWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID, true)

        val state = MetaDB.getWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard state can be enabled", state, equalTo(true))
    }

    @Test
    fun whiteboardStateCanBeDisabled() {
        // First enable, then disable
        MetaDB.storeWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID, true)
        MetaDB.storeWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID, false)

        val state = MetaDB.getWhiteboardState(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard state can be disabled", state, equalTo(false))
    }

    @Test
    fun whiteboardStateIsDeckSpecific() {
        val deck1 = 1L
        val deck2 = 2L

        // Enable for deck1 only
        MetaDB.storeWhiteboardState(targetContext, deck1, true)

        val state1 = MetaDB.getWhiteboardState(targetContext, deck1)
        val state2 = MetaDB.getWhiteboardState(targetContext, deck2)

        assertThat("Deck 1 whiteboard is enabled", state1, equalTo(true))
        assertThat("Deck 2 whiteboard is still disabled", state2, equalTo(false))
    }

    // endregion

    // region MetaDB Whiteboard Visibility Tests

    @Test
    fun whiteboardVisibilityIsHiddenByDefault() {
        val visibility = MetaDB.getWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard is hidden by default", visibility, equalTo(false))
    }

    @Test
    fun whiteboardVisibilityCanBeToggled() {
        MetaDB.storeWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID, true)

        val visibility = MetaDB.getWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard visibility can be shown", visibility, equalTo(true))
    }

    @Test
    fun whiteboardVisibilityCanBeHidden() {
        // First show, then hide
        MetaDB.storeWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID, true)
        MetaDB.storeWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID, false)

        val visibility = MetaDB.getWhiteboardVisibility(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat("Whiteboard visibility can be hidden", visibility, equalTo(false))
    }

    @Test
    fun whiteboardVisibilityIsDeckSpecific() {
        val deck1 = 1L
        val deck2 = 2L

        MetaDB.storeWhiteboardVisibility(targetContext, deck1, true)

        val visibility1 = MetaDB.getWhiteboardVisibility(targetContext, deck1)
        val visibility2 = MetaDB.getWhiteboardVisibility(targetContext, deck2)

        assertThat("Deck 1 whiteboard is visible", visibility1, equalTo(true))
        assertThat("Deck 2 whiteboard is still hidden", visibility2, equalTo(false))
    }

    // endregion
}
