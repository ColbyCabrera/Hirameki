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
object CongratsScreen

@Serializable
object StatisticsDestination

@Serializable
data class DeckOptionsDestination(val deckId: Long)

@Serializable
data class CardInfoDestination(val cardId: Long)

class Navigator(initialKey: Any) {
    val backStack = mutableStateListOf(initialKey)

    fun goTo(key: Any) {
        backStack.add(key)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }
}

@Composable
fun rememberNavigator(initialKey: Any): Navigator {
    return remember { Navigator(initialKey) }
}