// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.browser

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.CardsOrNotes

/**
 * Either a [CardId] or a [NoteId]. The ID of a row inside the 'Browser' can be either.
 *
 * A [CardOrNoteId] should always be associated with [CardsOrNotes].
 * It is not included in the class as this class is primarily provided in a [BrowserRowCollection]
 * and storing the same value for every item in the list is a waste
 */
@JvmInline
value class CardOrNoteId(
    val cardOrNoteId: Long,
) {
    override fun toString(): String = cardOrNoteId.toString()

    suspend fun toCardIdOrNull(type: CardsOrNotes): CardId? =
        when (type) {
            CardsOrNotes.CARDS -> cardOrNoteId
            CardsOrNotes.NOTES -> withCol { cardIdsOfNote(cardOrNoteId).firstOrNull() }
        }

    suspend fun toCardId(type: CardsOrNotes): CardId =
        toCardIdOrNull(type) ?: throw NoSuchElementException("No cards found for note $cardOrNoteId")
}
