/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.WhiteboardPenColor
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for whiteboard pen color persistence in MetaDB.
 * These tests verify the data layer behavior, not UI behavior.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerNoParamTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        MetaDB.resetDB(targetContext)
    }

    // region Whiteboard Pen Color Tests (MetaDB layer)

    @Test
    fun defaultWhiteboardColorIsUsedOnFirstRun() {
        // When no color is stored, MetaDB returns null (UI applies default)
        val retrievedColor = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat(
            "No color stored initially - null means use UI default",
            retrievedColor.lightPenColor,
            equalTo(null),
        )
    }

    @Test
    fun whiteboardLightModeColorIsUsed() {
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val retrievedColor = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat(
            "Light mode color is stored",
            retrievedColor.lightPenColor,
            equalTo(ARBITRARY_PEN_COLOR_VALUE),
        )
    }

    @Test
    fun whiteboardDarkModeColorIsUsed() {
        storeDarkModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val retrievedColor = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat(
            "Dark mode color is stored",
            retrievedColor.darkPenColor,
            equalTo(ARBITRARY_PEN_COLOR_VALUE),
        )
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseLight() {
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val penColorResult = penColor
        assertThat(
            "Light pen color is changed",
            penColorResult.lightPenColor,
            equalTo(ARBITRARY_PEN_COLOR_VALUE),
        )
    }

    @Test
    fun whiteboardPenColorChangeChangesDatabaseDark() {
        storeDarkModeColor(ARBITRARY_PEN_COLOR_VALUE)

        val penColorResult = penColor
        assertThat(
            "Dark pen color is changed",
            penColorResult.darkPenColor,
            equalTo(ARBITRARY_PEN_COLOR_VALUE),
        )
    }

    @Test
    fun whiteboardDarkPenColorIsNotUsedInLightMode() {
        storeDarkModeColor(555)

        val retrievedColor = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)
        // Dark mode color is 555, light mode color is null (use default)
        assertThat(
            "Light pen color is null (use UI default) when only dark is set",
            retrievedColor.lightPenColor,
            equalTo(null),
        )
        assertThat("Dark pen color is stored correctly", retrievedColor.darkPenColor, equalTo(555))
    }

    @Test
    fun differentDeckPenColorDoesNotAffectCurrentDeck() {
        val did = 2L
        storeLightModeColor(ARBITRARY_PEN_COLOR_VALUE, did)

        val retrievedColor = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)
        assertThat(
            "Pen color for default deck is null (use UI default)",
            retrievedColor.lightPenColor,
            equalTo(null),
        )
    }

    // endregion

    // region Helper Methods

    @Suppress("SameParameterValue")
    private fun storeDarkModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, false, value)
    }

    @Suppress("SameParameterValue")
    private fun storeLightModeColor(
        value: Int,
        did: DeckId,
    ) {
        MetaDB.storeWhiteboardPenColor(targetContext, did, true, value)
    }

    @Suppress("SameParameterValue")
    private fun storeLightModeColor(value: Int) {
        MetaDB.storeWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID, true, value)
    }

    private val penColor: WhiteboardPenColor
        get() = MetaDB.getWhiteboardPenColor(targetContext, Consts.DEFAULT_DECK_ID)

    // endregion

    companion object {
        const val DEFAULT_LIGHT_PEN_COLOR = Color.BLACK
        const val ARBITRARY_PEN_COLOR_VALUE = 555
    }
}
