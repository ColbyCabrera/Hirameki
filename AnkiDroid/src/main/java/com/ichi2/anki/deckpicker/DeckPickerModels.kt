// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.deckpicker

import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode

enum class DeckSelectionType {
    /** Show study options if fragmented, otherwise, review  */
    DEFAULT,

    /** Always show study options (if the deck counts are clicked)  */
    SHOW_STUDY_OPTIONS,

    /** Always open reviewer (keyboard shortcut)  */
    SKIP_STUDY_OPTIONS,
}

sealed class DeckSelectionResult {
    data class HasCardsToStudy(
        val selectionType: DeckSelectionType,
    ) : DeckSelectionResult()

    data class Empty(
        val deckId: DeckId,
    ) : DeckSelectionResult()

    data class NoCardsToStudy(
        val deckId: DeckId,
    ) : DeckSelectionResult()
}

fun DeckNode.onlyHasDefaultDeck() = children.singleOrNull()?.did == Consts.DEFAULT_DECK_ID
