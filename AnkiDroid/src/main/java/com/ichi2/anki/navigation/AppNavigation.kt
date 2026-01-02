/****************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that in editing this file it will be useful, *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or    *
 * FITNESS FOR A PARTICULAR PURPOSE.                                                    *
 * See the GNU General Public License for more details.                                 *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlinx.serialization.Serializable

@Serializable
object DeckPickerScreen

@Serializable
object HelpScreen

@Serializable
data class CongratsScreen(val deckId: Long)

@Serializable
object StatisticsDestination

@Serializable
data class DeckOptionsDestination(val deckId: Long)

@Serializable
data class CardInfoDestination(val cardId: Long)

/**
 * A simple navigator that manages a back stack of destination keys.
 *
 * The back stack is initialized with a single [initialKey].
 *
 * @param initialKey The initial destination key to start the back stack with.
 */
class Navigator(initialKey: Any) {
    private val _backStack = mutableStateListOf(initialKey)
    val backStack: List<Any> get() = _backStack

    /**
     * Pushes a new destination key onto the back stack.
     *
     * @param key The key representing the destination to navigate to.
     */
    fun goTo(key: Any) {
        _backStack.add(key)
    }

    /**
     * Pops the top destination key off the back stack.
     *
     * If the back stack contains only one item, this operation is a no-op.
     * This prevents the back stack from becoming empty.
     */
    fun goBack() {
        if (_backStack.size > 1) {
            _backStack.removeAt(_backStack.size - 1)
        }
    }
}

/**
 * Creates and remembers a [Navigator] instance.
 *
 * The [Navigator] is created once and retained across recompositions.
 * If the [initialKey] changes, the [Navigator] will be recreated.
 *
 * Typical Usage:
 * ```
 * val navigator = rememberNavigator(initialKey = DeckPickerScreen)
 * NavDisplay(backStack = navigator.backStack)
 * ```
 *
 * @param initialKey The start destination for the navigation back stack.
 * @return A remembered [Navigator] instance ready for use.
 */
@Composable
fun rememberNavigator(initialKey: Any): Navigator {
    return remember(initialKey) { Navigator(initialKey) }
}
