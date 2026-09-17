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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test

/** The card's shrink while it is dragged, the one piece of [CardMotionSpec] with arithmetic in it. */
class CardMotionSpecTest {
    private val spec = CardMotionSpec.Default

    @Test
    fun `the card runs from full size at rest to the drag floor at the corner`() {
        assertThat("full size at rest", spec.dragScaleAt(0f).toDouble(), closeTo(1.0, TOLERANCE))
        assertThat(
            "the drag floor at the corner, with the rest of the shrink left to the flight",
            spec.dragScaleAt(1f).toDouble(),
            closeTo(spec.dragFloorScale.toDouble(), TOLERANCE),
        )
    }

    @Test
    fun `a journey outside the trip to the corner stays at its ends`() {
        assertThat(spec.dragScaleAt(-1f), equalTo(spec.dragScaleAt(0f)))
        assertThat(spec.dragScaleAt(2f), equalTo(spec.dragScaleAt(1f)))
    }

    @Test
    fun `the card only ever gets smaller on the way to the corner`() {
        var previous = spec.dragScaleAt(0f)
        for (step in 1..STEPS) {
            val scale = spec.dragScaleAt(step.toFloat() / STEPS)
            assertThat("smaller $step/$STEPS of the way in", scale.toDouble(), lessThan(previous.toDouble()))
            previous = scale
        }
    }

    private companion object {
        const val TOLERANCE = 1e-6
        const val STEPS = 20
    }
}
