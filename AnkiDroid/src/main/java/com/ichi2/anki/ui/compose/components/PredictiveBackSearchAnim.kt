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

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ichi2.anki.ui.motion.PredictiveBack
import kotlinx.coroutines.CancellationException

/**
 * open progress of a search bar (0f closed, 1f open), for [AnkiSearchBar]'s `searchAnim`.
 *
 * it also owns back while the search is open: the bar follows a predictive back gesture
 * (android 14+), springs back open when the gesture is cancelled, and [onClose] runs when back
 * commits. [AnkiSearchBar] is a TextField, so it has none of material3 SearchBar's built-in
 * predictive back.
 *
 * the newest registered back handler wins, so call this where the screen's search back handler
 * used to be declared; that keeps its priority against the screen's other handlers.
 *
 * @param backEnabled false while something drawn over the open search owns back (a navigation
 * drawer). registration order can't express that: a search in Scaffold's topBar is composed during
 * layout, so its handler registers after the drawer's and would win while both are enabled.
 */
@Composable
fun predictiveBackSearchAnim(
    isOpen: Boolean,
    backEnabled: Boolean = true,
    onClose: () -> Unit,
): State<Float> {
    val anim = remember { Animatable(if (isOpen) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // bumped when a gesture is cancelled. it re-runs the effect below, which settles the bar at the
    // current isOpen: open after a plain cancel, closed if the search was closed mid-gesture
    var settleRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(isOpen, settleRequests) {
        anim.animateTo(if (isOpen) 1f else 0f, spec)
    }
    PredictiveBackHandler(enabled = isOpen && backEnabled) { backEvents ->
        try {
            // eased, not the raw progress: the same curve the rest of back uses, see [PredictiveBack]
            backEvents.collect { anim.snapTo(1f - PredictiveBack.ProgressEasing.transform(it.progress)) }
            onClose()
        } catch (e: CancellationException) {
            settleRequests++
            throw e
        }
    }
    return anim.asState()
}
