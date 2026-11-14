/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.GestureMapper

/**
 * Maps a gesture to the command bound to it for the card side being shown.
 *
 * @param currentSide the side of the card being shown: [CardSide.QUESTION] or [CardSide.ANSWER]
 */
class GestureProcessor(
    private val processor: ViewerCommand.CommandProcessor?,
    private val currentSide: () -> CardSide,
) {
    companion object {
        /** The 'Enable gestures' switch in the controls settings. */
        const val PREF_KEY = "gestures"

        /**
         * #185: on by default. No gesture is bound by default, so only gestures the user binds do
         * anything, and binding one no longer needs a second step to take effect.
         */
        const val DEFAULT_ENABLED = true

        fun isEnabledIn(preferences: SharedPreferences): Boolean = preferences.getBoolean(PREF_KEY, DEFAULT_ENABLED)

        /** Taps which are only recognised when 9-point touch is enabled */
        private val NINE_POINT_TAPS =
            setOf(Gesture.TAP_TOP_LEFT, Gesture.TAP_TOP_RIGHT, Gesture.TAP_CENTER, Gesture.TAP_BOTTOM_LEFT, Gesture.TAP_BOTTOM_RIGHT)
    }

    /** The commands bound to each gesture, with the side they apply to, in [ViewerCommand] order */
    private var bindings: Map<Gesture, List<Pair<ViewerCommand, CardSide>>> = emptyMap()
    private val gestureMapper = GestureMapper()

    /**
     * Whether the class has been enabled.
     * This requires the [PREF_KEY] preference is enabled, and [GestureProcessor.init] has been called
     */
    var isEnabled = false
        private set

    fun init(preferences: SharedPreferences) {
        isEnabled = isEnabledIn(preferences)
        gestureMapper.init(preferences)

        val useCornerTouch = preferences.getBoolean("gestureCornerTouch", false)
        val unsupported = if (useCornerTouch) emptySet() else NINE_POINT_TAPS
        bindings =
            ViewerCommand.entries
                .flatMap { command ->
                    command.getBindings(preferences).mapNotNull { binding ->
                        val gesture = (binding.binding as? Binding.GestureInput)?.gesture
                        if (gesture == null || gesture in unsupported) null else Triple(gesture, command, binding.side)
                    }
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second to it.third })
    }

    fun onTap(
        height: Int,
        width: Int,
        posX: Float,
        posY: Float,
    ): Boolean {
        val gesture = gestureMapper.gesture(height, width, posX, posY) ?: return false
        return execute(gesture)
    }

    fun onDoubleTap(): Boolean = execute(Gesture.DOUBLE_TAP)

    fun onFling(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        isSelecting: Boolean,
        isXScrolling: Boolean,
        isYScrolling: Boolean,
    ): Boolean {
        val gesture = gestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling)
        return execute(gesture)
    }

    fun onShake(): Boolean = execute(Gesture.SHAKE)

    /** A tap with [fingers] fingers at once, from 2 to 4 */
    fun onMultiFingerTap(fingers: Int): Boolean =
        execute(
            when (fingers) {
                2 -> Gesture.TWO_FINGER_TAP
                3 -> Gesture.THREE_FINGER_TAP
                4 -> Gesture.FOUR_FINGER_TAP
                else -> null
            },
        )

    /**
     * Runs the first command bound to [gesture] for the side being shown, trying the next one if
     * a command doesn't apply. Matches [com.ichi2.anki.reviewer.BindingMap.onGesture].
     */
    private fun execute(gesture: Gesture?): Boolean {
        val candidates = gesture?.let { bindings[it] } ?: return false
        val side = currentSide()
        for ((command, bindingSide) in candidates) {
            if (bindingSide != CardSide.BOTH && bindingSide != side) continue
            if (processor?.executeCommand(command, gesture) == true) return true
        }
        return false
    }

    /**
     * Whether one of the provided gestures is bound, on either side of the card
     * @param gestures the gestures to check
     * @return `false` if none of the gestures are bound. `true` otherwise
     */
    fun isBound(vararg gestures: Gesture): Boolean {
        if (!isEnabled) return false

        return gestures.any { it in bindings }
    }
}
