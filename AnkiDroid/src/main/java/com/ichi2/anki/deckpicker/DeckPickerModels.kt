/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
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

    data class NoCardsToStudy(val deckId: DeckId) : DeckSelectionResult()
}

fun DeckNode.onlyHasDefaultDeck() = children.singleOrNull()?.did == Consts.DEFAULT_DECK_ID
