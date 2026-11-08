/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.reviewer.card

import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** What a card transition acts on. */
interface CardTransitionStage {
    /** Whether the page has a card on screen that a snapshot could capture. */
    val hasShownCard: Boolean

    /** The card surface exactly as it is on screen, or `null` if it cannot be captured. */
    suspend fun captureFrame(): ImageBitmap?

    /** Returns once an overlay set before this call has reached the screen. */
    suspend fun awaitOverlayDrawn()

    fun show(display: CardDisplay)

    /** Returns once [display] is laid out and painted, under the overlay. */
    suspend fun awaitShown(display: CardDisplay)

    /** Animates the overlay's opacity from 1 to 0. */
    suspend fun fade(onOpacity: (Float) -> Unit)
}

/**
 * Crossfades between cards in one WebView: a snapshot of the current frame covers the WebView while
 * the card is swapped underneath, then fades out to reveal it.
 *
 * A new target cancels the transition in progress and starts another from whatever is on screen.
 */
@MainThread
class CardTransitionCoordinator(
    private val stage: CardTransitionStage,
    private val isCrossfadeEnabled: () -> Boolean,
) {
    var overlay by mutableStateOf<ImageBitmap?>(null)
        private set

    var overlayOpacity by mutableFloatStateOf(0f)
        private set

    private val target = MutableStateFlow<CardDisplay?>(null)

    fun setTarget(display: CardDisplay) {
        if (display.baseUrl.isNotEmpty()) target.value = display
    }

    suspend fun run() = target.filterNotNull().collectLatest(::transitionTo)

    private suspend fun transitionTo(display: CardDisplay) {
        if (!coverCurrentFrame()) {
            removeOverlay()
            stage.show(display)
            return
        }
        stage.awaitOverlayDrawn()
        stage.show(display)
        withTimeoutOrNull(SHOW_TIMEOUT) { stage.awaitShown(display) }
        stage.fade { overlayOpacity = it }
        removeOverlay()
    }

    /** @return whether an opaque overlay now shows the current frame */
    private suspend fun coverCurrentFrame(): Boolean {
        if (overlay != null && overlayOpacity == 1f) return true
        val hasFrameToCover = overlay != null || stage.hasShownCard
        if (!hasFrameToCover || !isCrossfadeEnabled()) return false
        overlay = stage.captureFrame() ?: return false
        overlayOpacity = 1f
        return true
    }

    private fun removeOverlay() {
        overlay = null
        overlayOpacity = 0f
    }

    private companion object {
        /** Longer than the page's own limits on loading images and drawing masks. */
        val SHOW_TIMEOUT = 1500.milliseconds
    }
}
