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
package com.ichi2.anki.ui.motion

import androidx.activity.BackEventCompat
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.fragment.app.FragmentTransaction

/**
 * one definition of how back looks in this app, for every screen that animates its own back.
 *
 * leaving an activity is drawn by the system and no app can change it: the closing window shrinks to
 * 0.9, keeps the device's window corner radius, and the screen behind enters from 96dp under a scrim
 * (aosp `CrossActivityBackAnimation.MAX_SCALE` / `cross_activity_back_entering_start_offset`). so
 * in-app back copies that shape instead of inventing one, and the whole app reads as one motion
 * whether back leaves an activity, a settings sub-screen or a nav3 entry.
 *
 * the numbers come from material 3's predictive back guidance (exit 100->90%, enter 110->100%, a
 * late fade) and from `MaterialBackAnimationHelper`, whose progress interpolator is
 * `PathInterpolator(0.1, 0.1, 0, 1)`. corner radius is deliberately absent: only the system rounds a
 * closing window, and an in-app screen is not a window of its own.
 *
 * this is motion only. which handler owns back is decided per screen, and taking back over with an
 * enabled callback is what switches the system animation off, so those decisions stay where they are.
 */
object PredictiveBack {
    /** smallest a leaving screen gets. matches aosp's `MAX_SCALE` and MDC's `MaterialMainContainerBackHelper`. */
    const val MIN_SCALE = 0.9f

    /** how large the screen behind starts, mirrored from [MIN_SCALE] so entering and leaving agree. */
    const val ENTER_SCALE = 1.1f

    /** gesture progress easing: the curve MDC's back helpers use, so app and components match. */
    val ProgressEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /** how long a committed (non-gesture) back takes. a gesture ignores it and seeks instead. */
    const val DURATION_MS = 300

    /**
     * how long both screens hold full opacity before either of them fades. read off
     * `fragment_close_enter`/`fragment_close_exit`, whose alpha animators hold for 66ms inside a
     * 300ms set: settings is animated by those files and cannot be given other numbers without
     * app-owned animator resources, so every other screen takes fragment's instead.
     */
    const val FADE_DELAY_MS = 66

    /** how long the cross-fade itself takes, from the same two fragment animators. */
    const val FADE_DURATION_MS = 50

    /**
     * settings sub-screens. fragment's built-in close animators are where this motion is taken from
     * - scale 1.0->0.9 over a screen entering at 1.1, on `fragment_fast_out_extra_slow_in`, which
     * has the same control points as aosp's `Interpolators.EMPHASIZED`, and the alpha hold and fade
     * this file spends everywhere else, [FADE_DELAY_MS] and [FADE_DURATION_MS] - and they are
     * seekable, so the gesture previews the screen behind. committing with OPEN is what plays them:
     * `FragmentManager.reverseTransit` turns OPEN into CLOSE on a pop.
     */
    const val FRAGMENT_TRANSIT = FragmentTransaction.TRANSIT_FRAGMENT_OPEN

    /** nav3 forward navigation: the new screen grows in from [MIN_SCALE] over the one it replaces. */
    fun push(): ContentTransform = scaleIn(scaleSpec(), initialScale = MIN_SCALE) + fadeIn(fadeSpec()) togetherWith fadeOut(fadeSpec())

    /** nav3 back that was tapped, not swiped: [predictivePop] with no edge to drift away from. */
    fun pop(): ContentTransform = predictivePop(BackEventCompat.EDGE_NONE)

    /**
     * nav3 back that is being swiped. nav3 seeks this with the gesture, so the spec only describes the
     * end state.
     *
     * [swipeEdge] is where the finger came from, one of [BackEventCompat]'s `EDGE_` values (nav3 hands
     * over the same numbers). the leaving screen shrinks away from that edge, which is what
     * `MaterialMainContainerBackHelper` does: `translationX = lerp(0, maxShift, progress) *
     * (leftSwipeEdge ? 1 : -1)`. pinning the opposite edge as the scale pivot produces that shift
     * without a second animated property.
     */
    fun predictivePop(swipeEdge: Int): ContentTransform =
        scaleIn(scaleSpec(), initialScale = ENTER_SCALE) + fadeIn(fadeSpec()) togetherWith
            scaleOut(scaleSpec(), targetScale = MIN_SCALE, transformOrigin = leavingPivot(swipeEdge)) + fadeOut(fadeSpec())

    /** the edge held still while a screen shrinks, so it drifts away from the finger. */
    internal fun leavingPivot(swipeEdge: Int): TransformOrigin =
        when (swipeEdge) {
            BackEventCompat.EDGE_LEFT -> TransformOrigin(1f, 0.5f)
            BackEventCompat.EDGE_RIGHT -> TransformOrigin(0f, 0.5f)
            else -> TransformOrigin.Center
        }

    private fun scaleSpec() = tween<Float>(DURATION_MS, easing = ProgressEasing)

    private fun fadeSpec() = tween<Float>(FADE_DURATION_MS, delayMillis = FADE_DELAY_MS)
}
