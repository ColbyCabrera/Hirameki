/*
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
