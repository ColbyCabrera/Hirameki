// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DeckPickerScreen : NavKey

@Serializable
data object HelpScreen : NavKey

@Serializable
data object ContributeScreen : NavKey

@Serializable
data class CongratsScreen(
    val deckId: Long,
) : NavKey

@Serializable
data object StatisticsDestination : NavKey

@Serializable
data class DeckOptionsDestination(
    val deckId: Long,
) : NavKey

@Serializable
data class CardInfoDestination(
    val cardId: Long,
) : NavKey

@Serializable
data object ManageNoteTypesDestination : NavKey
