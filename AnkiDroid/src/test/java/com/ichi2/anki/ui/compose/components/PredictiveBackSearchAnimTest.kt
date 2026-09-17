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

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.ui.motion.PredictiveBack
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The search bar's own predictive back, which is the reason [predictiveBackSearchAnim] exists.
 *
 * The screens that use it are only tested through a completed back press, and that path would also work
 * with a plain BackHandler. What has no other cover is the gesture: the bar following a finger across the
 * screen, and settling back open when that finger is lifted without committing.
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackSearchAnimTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** the host activity's dispatcher, captured by [setSearchContent] */
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** how open the bar is drawn, 1f fully open and 0f closed */
    private var openProgress = 0f

    /** how often back has asked the screen to close its search */
    private var closes = 0

    @Test
    fun `a gesture that is let go settles the bar back open`() {
        setSearchContent()

        startGesture()
        progressGesture(0.5f)
        assertThat("the bar follows the finger", openProgress, lessThan(1f))

        composeTestRule.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
        composeTestRule.waitForIdle()

        // the animatable is left wherever the finger abandoned it, so something has to drive it home
        assertThat("the bar is fully open again", openProgress, equalTo(1f))
        assertThat("a gesture that is let go closes nothing", closes, equalTo(0))
    }

    @Test
    fun `a gesture carried through closes the search`() {
        setSearchContent()

        startGesture()
        progressGesture(0.9f)
        composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertThat("back closed the search", closes, equalTo(1))
        assertThat("the bar finishes closed", openProgress, equalTo(0f))
    }

    @Test
    fun `the bar ignores a gesture while something over it owns back`() {
        setSearchContent(backEnabled = false)

        startGesture()
        progressGesture(0.5f)

        // a drawer over the open search owns back; the bar moving under it would be drawing someone else's gesture
        assertThat("the bar stays put", openProgress, equalTo(1f))
        assertThat("and closes nothing", closes, equalTo(0))
    }

    @Test
    fun `the bar follows the app's back easing, not the raw gesture`() {
        setSearchContent()

        startGesture()
        progressGesture(0.5f)

        // raw progress would move the bar on a different curve from every other back animation in the app
        val eased = 1f - PredictiveBack.ProgressEasing.transform(0.5f)
        assertThat(openProgress.toDouble(), closeTo(eased.toDouble(), TOLERANCE))
    }

    /** an open search bar that closes itself when back commits, as every screen using it does */
    private fun setSearchContent(backEnabled: Boolean = true) {
        composeTestRule.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            var isOpen by remember { mutableStateOf(true) }
            AnkiDroidTheme {
                val progress by predictiveBackSearchAnim(isOpen, backEnabled = backEnabled) {
                    closes++
                    isOpen = false
                }
                openProgress = progress
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun startGesture() {
        composeTestRule.runOnIdle { backDispatcher.dispatchOnBackStarted(backEvent(0f)) }
        composeTestRule.waitForIdle()
    }

    private fun progressGesture(progress: Float) {
        composeTestRule.runOnIdle { backDispatcher.dispatchOnBackProgressed(backEvent(progress)) }
        composeTestRule.waitForIdle()
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private companion object {
        /** the bar is drawn from a float, so an exact curve value is not something to assert on the nose */
        const val TOLERANCE = 0.001
    }
}
