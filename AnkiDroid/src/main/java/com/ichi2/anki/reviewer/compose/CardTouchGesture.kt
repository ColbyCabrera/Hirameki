// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.compose

import com.ichi2.anki.cardviewer.GestureProcessor

/**
 * A raw touch gesture on the card, in the card WebView's pixel coordinates.
 * [GestureProcessor] maps it to the command bound in the gesture settings.
 */
sealed interface CardTouchGesture {
    data class Tap(
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
    ) : CardTouchGesture

    data object DoubleTap : CardTouchGesture

    data class Fling(
        val dx: Float,
        val dy: Float,
        val velocityX: Float,
        val velocityY: Float,
        /** The card scrolled horizontally during the gesture, so it was not a swipe. */
        val isXScrolling: Boolean,
        /** The card scrolled vertically during the gesture, so it was not a swipe. */
        val isYScrolling: Boolean,
    ) : CardTouchGesture
}

/** Sends [gesture] to [GestureProcessor], or does nothing if gestures are disabled. */
fun GestureProcessor.onCardTouchGesture(gesture: CardTouchGesture) {
    if (!isEnabled) return
    when (gesture) {
        is CardTouchGesture.Tap -> onTap(gesture.height, gesture.width, gesture.x, gesture.y)
        CardTouchGesture.DoubleTap -> onDoubleTap()
        is CardTouchGesture.Fling ->
            onFling(
                dx = gesture.dx,
                dy = gesture.dy,
                velocityX = gesture.velocityX,
                velocityY = gesture.velocityY,
                isSelecting = false,
                isXScrolling = gesture.isXScrolling,
                isYScrolling = gesture.isYScrolling,
            )
    }
}
