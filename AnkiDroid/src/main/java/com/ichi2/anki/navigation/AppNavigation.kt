// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
object DeckPickerScreen : NavKey

@Serializable
object HelpScreen : NavKey

@Serializable
object ContributeScreen : NavKey

@Serializable
data class CongratsScreen(
    val deckId: Long,
) : NavKey

@Serializable
object StatisticsDestination : NavKey

@Serializable
data class DeckOptionsDestination(
    val deckId: Long,
) : NavKey

@Serializable
data class CardInfoDestination(
    val cardId: Long,
) : NavKey

@Serializable
object ManageNoteTypesDestination : NavKey
