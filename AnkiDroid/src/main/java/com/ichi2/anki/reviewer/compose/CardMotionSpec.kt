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

import androidx.compose.runtime.Immutable
import kotlin.math.pow

/**
 * Every number behind the reviewer card's gesture, in one place.
 *
 * The card is dragged toward a corner to grade it, and how it looks on the way in is a blend of two
 * behaviours chosen by how fast it was released: a slow, guided release makes it neck ([funnelNarrow]),
 * a fast throw makes it tumble ([tumbleDegrees]). Everything is a fraction of the card's own size or of
 * the trip to the corner, so the feel survives any screen size.
 *
 * Tune the gesture here. [DraggableFlashcard] keeps the numbers that belong to how the card is drawn rather
 * than to how the gesture behaves, such as its resting and lifted shadow, the camera distance its layers
 * share, and the point in the flight where a graded card dissolves; it also keeps two floors that only hold
 * the geometry together, the slowest a graded flight may travel and the floor under the card's neck.
 */
@Immutable
data class CardMotionSpec(
    /** Card shape as width divided by height: a bank card stood on its end (ISO/IEC 7810 ID-1). */
    val cardAspectRatio: Float = 53.98f / 85.6f,
    /** How much of the screen the card may cover, leaving the corners clear on every side. */
    val cardSizeFraction: Float = 0.68f,
    /**
     * Smallest the card gets while a finger is still on it. It only shrinks past this once released,
     * so nothing ever disappears mid-drag.
     */
    val dragFloorScale: Float = 0.2f,
    /** Size the card has shrunk to by the time it reaches the corner, as a fraction of full size. */
    val minScale: Float = 0.08f,
    /**
     * Shape of the shrink against distance travelled. 1 is linear; above 1 keeps the card large until
     * it is close to the corner, then shrinks hard.
     */
    val shrinkCurve: Float = 1.15f,
    /**
     * How far along the trip to a corner the card's centre must be for a release to grade it. Measured
     * on where the card is drawn, not on how far the thumb has travelled.
     */
    val registerAt: Float = 0.55f,
    /** How far the flight path bows sideways, as a fraction of its length. 0 is a straight line. */
    val pathCurve: Float = 0.14f,
    /**
     * A card within this fraction of its shorter side of either axis through the middle picks no corner at all, so a
     * flick straight down or sideways, aimed between two corners, grades neither.
     */
    val deadZone: Float = 0.06f,
    /**
     * How far outside the card, as a fraction of its shorter side, a touch still picks it up. Beyond
     * that the gesture belongs to whatever is under the finger.
     */
    val grabMargin: Float = 0.06f,
    /** Where a corner sits, as a fraction of the container's shorter side in from that corner. */
    val cornerInset: Float = 0.12f,
    /** Sideways lean while dragging, at the point where the card has crossed a full card width. */
    val tiltDegrees: Float = 9f,
    // ---- the two release behaviours, blended by speed ----
    /**
     * Release speed toward the corner, in card widths per second, at or below which the card only
     * necks. Only the part of the motion heading for the corner counts.
     */
    val funnelSpeed: Float = 0.6f,
    /**
     * Release speed toward the corner, in card widths per second, at or above which it only tumbles.
     * The tumble grows in across the whole flight, since the card only necks while it is held.
     */
    val throwSpeed: Float = 3.2f,
    /**
     * Release speed toward the corner, in card widths per second, at which a release counts as a throw
     * and grades even short of [registerAt]. The same speed back toward the middle cancels a grade.
     */
    val flickSpeed: Float = 1.5f,
    /** How far along the trip to a corner a throw must have carried the card before it counts. */
    val flickReach: Float = 0.22f,
    /** How far the card pitches head over heels on a full-speed throw. */
    val tumbleDegrees: Float = 150f,
    /** How much the card narrows across its direction of travel when guided in slowly. */
    val funnelNarrow: Float = 0.45f,
    /** How much it stretches along that direction at the same time. */
    val funnelStretch: Float = 0.2f,
    // ---- timings ----
    /** Longest the flight into a corner may take; a faster release always lands sooner. */
    val maxDropMillis: Int = 380,
    /** Shortest that flight may take, so even a violent throw stays legible. */
    val minDropMillis: Int = 120,
    /** How long the card takes to drift home when released without grading. */
    val homeMillis: Int = 300,
    /**
     * How long a graded card stays hidden waiting for the next one before it is shown again. The view
     * model ignores a rating while another card action is still running, and a rating that never
     * lands must not leave the reviewer with no card on screen.
     */
    val awayTimeoutMillis: Int = 2500,
    /**
     * Longest a new card stays out of sight waiting for the page of the face that is up to report it drawn, and a
     * reveal waits for the answer, before going ahead regardless. An in-place swap normally paints within a few
     * frames; reviewer.js itself gives a card's fonts at most 800 ms before swapping.
     */
    val paintWaitMillis: Int = 1000,
    /** How long the next card takes to fade in. */
    val enterMillis: Int = 240,
    /** How long the card takes to turn over when the answer is revealed. */
    val flipMillis: Int = 400,
    /** How long the corner wells take to appear once the answer is showing, and to fade when it is not. */
    val wellFadeMillis: Int = 560,
    /** How strongly an untouched well glows once it can be used, so the corners announce themselves. */
    val wellIdleAlpha: Float = 0.1f,
    /** How long the corner takes to ripple and settle once a card has sunk into it. */
    val landMillis: Int = 520,
    /** How long the card's shadow takes to lift when picked up and to settle when put down. */
    val liftMillis: Int = 220,
    /**
     * How quickly a corner's glow chases the card each frame, 0..1. Low is a slow, soft bloom; 1 makes
     * the colour snap the instant the card crosses into another corner.
     */
    val wellSmoothing: Float = 0.12f,
) {
    /** Release speed as a 0..1 blend between [funnelSpeed] (guided) and [throwSpeed] (thrown). */
    fun throwiness(cardWidthsPerSecond: Float): Float {
        if (throwSpeed <= funnelSpeed) return 0f
        return ((cardWidthsPerSecond - funnelSpeed) / (throwSpeed - funnelSpeed)).coerceIn(0f, 1f)
    }

    /**
     * Card size while it is being dragged, at [journey] from 0 at rest to 1 at the corner. It bottoms
     * out at [dragFloorScale]; the rest of the shrink belongs to the flight in, after release.
     */
    fun dragScaleAt(journey: Float): Float {
        val j = journey.coerceIn(0f, 1f)
        return 1f - (1f - dragFloorScale) * j.pow(shrinkCurve)
    }

    companion object {
        val Default = CardMotionSpec()
    }
}
