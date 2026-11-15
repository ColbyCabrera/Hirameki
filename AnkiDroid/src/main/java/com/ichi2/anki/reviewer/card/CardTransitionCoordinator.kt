// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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

/** A snapshot of the card surface, covering the WebView. */
class CardOverlay(
    val frame: ImageBitmap,
) {
    var opacity by mutableFloatStateOf(1f)
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
    var overlay by mutableStateOf<CardOverlay?>(null)
        private set

    private val target = MutableStateFlow<CardDisplay?>(null)

    fun setTarget(display: CardDisplay) {
        target.value = display
    }

    suspend fun run() = target.filterNotNull().collectLatest(::transitionTo)

    private suspend fun transitionTo(display: CardDisplay) {
        val cover = coverForCurrentFrame()
        overlay = cover
        if (cover == null) {
            stage.show(display)
            return
        }
        stage.awaitOverlayDrawn()
        stage.show(display)
        withTimeoutOrNull(SHOW_TIMEOUT) { stage.awaitShown(display) }
        stage.fade { cover.opacity = it }
        overlay = null
    }

    /** @return an opaque overlay showing the current frame, or `null` if there can be none */
    private suspend fun coverForCurrentFrame(): CardOverlay? {
        val current = overlay
        if (current?.opacity == 1f) return current
        val hasFrameToCover = current != null || stage.hasShownCard
        if (!hasFrameToCover || !isCrossfadeEnabled()) return null
        return stage.captureFrame()?.let(::CardOverlay)
    }

    private companion object {
        /** Longer than the page's own limits on loading images and drawing masks. */
        val SHOW_TIMEOUT = 1500.milliseconds
    }
}
