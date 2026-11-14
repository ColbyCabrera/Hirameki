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

    /** A quick tap with 2 to 4 [fingers] at once, without moving them. */
    data class MultiFingerTap(
        val fingers: Int,
    ) : CardTouchGesture

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
        is CardTouchGesture.MultiFingerTap -> onMultiFingerTap(gesture.fingers)
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

/** Evaluates to `true` if the card has a text selection. A touch then ends the selection, not a gesture. */
internal const val HAS_TEXT_SELECTION_SCRIPT = """(() => { const s = document.getSelection(); return !!s && !s.isCollapsed; })();"""

/**
 * Evaluates to `true` if the touch at ([fractionX], [fractionY]) of the card's width and height
 * lands on an element which handles touches itself, such as a link, a button or an input.
 *
 * Matches `isInteractable` in upstream's `assets/scripts/ankidroid-reviewer.js` (new study screen),
 * plus `<select>` and `<textarea>`.
 */
internal fun interactiveElementScript(
    fractionX: Float,
    fractionY: Float,
): String {
    val fx = fractionX.coerceIn(0f, 1f)
    val fy = fractionY.coerceIn(0f, 1f)
    return """
        (() => {
            const vv = window.visualViewport;
            const x = vv ? vv.offsetLeft + $fx * vv.width : $fx * window.innerWidth;
            const y = vv ? vv.offsetTop + $fy * vv.height : $fy * window.innerHeight;
            const interactive = ["A", "BUTTON", "VIDEO", "SUMMARY", "INPUT", "SELECT", "TEXTAREA"];
            for (let node = document.elementFromPoint(x, y); node && node !== document; node = node.parentNode) {
                if (interactive.includes(node.nodeName) || node.onclick || node.isContentEditable ||
                    node.classList?.contains("tappable")) {
                    return true;
                }
            }
            return false;
        })();
        """.trimIndent()
}
