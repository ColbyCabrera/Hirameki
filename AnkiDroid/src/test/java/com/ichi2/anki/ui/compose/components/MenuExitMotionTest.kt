/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.ui.compose.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MenuExitMotion], which every compose menu in the app closes through.
 *
 * a material3 menu keeps its popup until its exit transition settles, with or without the wrapper, so presence
 * alone cannot tell a visible exit from the ~107ms fade the wrapper replaces. the alpha spring can: the menu
 * picks it in the popup's own composition, and a running exit only adopts the spring seen in the pass where
 * its target turned. so the test records, for every pass of the menu's content, the target and the spring.
 */
@RunWith(AndroidJUnit4::class)
class MenuExitMotionTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val theme = MotionScheme.expressive()

    private var expanded by mutableStateOf(false)

    /** every composition pass of the menu's content: the transition target it saw, and the alpha spring */
    private val passes = mutableListOf<Pair<Boolean, FiniteAnimationSpec<Float>>>()

    @Test
    fun `a closing menu fades on the slower effects spring, stays through its exit, then leaves`() {
        // the springs are stepped frame by frame, so the clock only moves when the test moves it
        composeTestRule.mainClock.autoAdvance = false
        setMenuContent()
        val fastFade = theme.fastEffectsSpec<Float>()
        val slowerFade = theme.defaultEffectsSpec<Float>()
        assertThat("the two springs differ, so the checks below mean something", slowerFade, not(equalTo(fastFade)))

        expanded = true
        frames(SETTLE_FRAMES)
        composeTestRule.onNodeWithText(ITEM).assertExists("the menu opened")
        assertThat("the entrance was composed", passes.isNotEmpty(), equalTo(true))
        assertThat("the entrance keeps the theme's fast fade", passes, equalTo(passes.map { true to fastFade }))

        passes.clear()
        expanded = false
        frames(2)
        assertThat("the exit was composed", passes.isNotEmpty(), equalTo(true))
        // a pass that saw the target turn but not yet the slower spring would show up here, and would be the one
        // the exit keeps
        assertThat(
            "from the pass the target turned, the menu fades on the slower spring",
            passes,
            equalTo(passes.map { false to slowerFade }),
        )

        frames(MID_EXIT_FRAMES)
        composeTestRule.onNodeWithText(ITEM).assertExists("the menu is still on screen mid-exit")

        frames(SETTLE_FRAMES)
        composeTestRule.onNodeWithText(ITEM).assertDoesNotExist()
    }

    // one frame at a time, idling in between: with the clock held, frames advanced without an idle between them
    // never brought the opened popup up at all
    private fun frames(count: Int) =
        repeat(count) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
        }

    private fun setMenuContent() {
        composeTestRule.setContent {
            AnkiDroidTheme {
                Box {
                    MenuExitMotion(expanded = expanded) {
                        // DropdownMenu hands its own transition the target like this, in this same pass; its popup
                        // reads it back in a composition of its own, which is where the alpha spring is picked
                        val target = remember { MutableTransitionState(false) }
                        target.targetState = expanded
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            val seenTarget = target.targetState
                            val seenFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                            SideEffect { passes += seenTarget to seenFade }
                            DropdownMenuItem(text = { Text(ITEM) }, onClick = {})
                        }
                    }
                }
            }
        }
        frames(1)
    }

    private companion object {
        const val ITEM = "menu item"

        /** ~480ms, well past either spring settling */
        const val SETTLE_FRAMES = 30

        /** with the two frames before it, ~130ms into the exit: the old fade had finished by then, the popup not */
        const val MID_EXIT_FRAMES = 6
    }
}
