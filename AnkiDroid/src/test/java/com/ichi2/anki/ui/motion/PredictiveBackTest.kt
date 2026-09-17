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
package com.ichi2.anki.ui.motion

import androidx.activity.BackEventCompat
import androidx.compose.ui.graphics.TransformOrigin
import androidx.fragment.app.FragmentTransaction
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.Test

/**
 * The shared back motion's numbers, which nothing else can check: a wrong one is invisible except
 * that back stops looking like the rest of the app.
 */
class PredictiveBackTest {
    @Test
    fun `settings is handed the transit that animates`() {
        // fragment plays its close animators for OPEN only - reverseTransit turns it into CLOSE on a
        // pop. NONE or FADE still compiles and still pops, and the motion silently disappears
        assertThat(
            "settings animates with fragment's own open-close animators",
            PredictiveBack.FRAGMENT_TRANSIT,
            equalTo(FragmentTransaction.TRANSIT_FRAGMENT_OPEN),
        )
    }

    @Test
    fun `the screen behind starts as far above full size as the leaving one ends below it`() {
        assertThat(
            "entering and leaving mirror around full size",
            (PredictiveBack.ENTER_SCALE - 1f).toDouble(),
            closeTo((1f - PredictiveBack.MIN_SCALE).toDouble(), TOLERANCE),
        )
    }

    @Test
    fun `both screens are solid for most of the gesture, then cross-fade well before it ends`() {
        // the shape fragment's alpha animators have: a hold, then a fade several times shorter, both
        // over long before the 300ms scale. two see-through screens at once is the bug this prevents
        assertThat(
            "the fade is short next to the hold before it",
            PredictiveBack.FADE_DURATION_MS,
            lessThan(PredictiveBack.FADE_DELAY_MS),
        )
        assertThat(
            "the cross-fade is over while the screens are still scaling",
            PredictiveBack.FADE_DELAY_MS + PredictiveBack.FADE_DURATION_MS,
            lessThan(PredictiveBack.DURATION_MS),
        )
        assertThat("nothing fades immediately", PredictiveBack.FADE_DELAY_MS, greaterThan(0))
    }

    @Test
    fun `a leaving screen shrinks away from the edge the finger came from`() {
        assertThat(
            "swiped from the left, the right edge is pinned",
            PredictiveBack.leavingPivot(BackEventCompat.EDGE_LEFT),
            equalTo(TransformOrigin(1f, 0.5f)),
        )
        assertThat(
            "swiped from the right, the left edge is pinned",
            PredictiveBack.leavingPivot(BackEventCompat.EDGE_RIGHT),
            equalTo(TransformOrigin(0f, 0.5f)),
        )
    }

    @Test
    fun `a back with no edge to it shrinks in place`() {
        assertThat(
            "a tapped back has no finger to drift away from",
            PredictiveBack.leavingPivot(BackEventCompat.EDGE_NONE),
            equalTo(TransformOrigin.Center),
        )
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
