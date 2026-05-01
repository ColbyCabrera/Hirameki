/****************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                       *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                                 *
 * See the GNU General Public License for more details.                                 *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.navigation

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object DeckPickerScreen : NavKey

@Serializable
object HelpScreen : NavKey

@Serializable
object ContributeScreen : NavKey

@Serializable
data class CongratsScreen(val deckId: Long) : NavKey

@Serializable
object StatisticsDestination : NavKey

@Serializable
data class DeckOptionsDestination(val deckId: Long) : NavKey

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Serializable
data class CardInfoDestination(val cardId: Long) : NavKey

@Serializable
object ManageNoteTypesDestination : NavKey

