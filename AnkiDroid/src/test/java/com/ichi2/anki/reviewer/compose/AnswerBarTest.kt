/*
 * Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.reviewer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The answer bar holding its content within its own width, whatever the screen, the language or the font scale. */
@RunWith(AndroidJUnit4::class)
class AnswerBarTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the typed answer field is no wider than the bar on a wide screen`() {
        showBar(width = 1000.dp, isAnswerShown = false, showTypeInAnswer = true)

        val field = composeTestRule.onNode(hasSetTextAction()).getBoundsInRoot()
        assertThat("400dp is the bar's cap", field.right - field.left, lessThanOrEqualTo(400.dp))
    }

    // measured for real: robolectric's default legacy graphics measure every character as 1px at any size, so a label
    // never overflowed and this passed on the clipping code it guards against
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `a show answer label too long for the bar stays on one line within it`() {
        showBar(width = NARROW, isAnswerShown = false, fontScale = LARGE_FONT_SCALE)

        val layout = textLayout(composeTestRule.onNode(hasText(getResourceString(R.string.show_answer)), useUnmergedTree = true))
        assertThat("clipped mid-letter at the bar's edge", layout.didOverflowWidth, equalTo(false))
        assertThat(layout.lineCount, equalTo(1))
    }

    private fun showBar(
        width: Dp,
        isAnswerShown: Boolean,
        showTypeInAnswer: Boolean = false,
        fontScale: Float = 1f,
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AnkiDroidTheme {
                    Box(Modifier.requiredWidth(width)) {
                        AnswerButtons(
                            isAnswerShown = isAnswerShown,
                            showButtonBadges = true,
                            showTypeInAnswer = showTypeInAnswer,
                            typedAnswer = "",
                            onTypedAnswerChanged = {},
                            onShowAnswer = {},
                            onRateCard = {},
                            nextTimes = listOf("1m", "6m", "10m", "4d"),
                            onMoreOptionsClick = {},
                        )
                    }
                }
            }
        }
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val getLayout =
            requireNotNull(node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)) {
                "no text layout"
            }
        getLayout.action?.invoke(results)
        return results.single()
    }

    private companion object {
        /** the largest font scale android offers */
        const val LARGE_FONT_SCALE = 2f

        /** a small phone's width */
        val NARROW = 300.dp
    }
}
