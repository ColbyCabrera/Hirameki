// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.model

import android.content.Context
import android.os.Parcelable
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import kotlinx.parcelize.Parcelize

/**
 * Either a deck in the collection, or [AllDecks]
 */
sealed class SelectableDeck : Parcelable {
    @Parcelize
    data object AllDecks : SelectableDeck()

    @Parcelize
    data class Deck(
        val deckId: DeckId,
        val name: String,
    ) : SelectableDeck() {
        constructor(d: DeckNameId) : this(d.id, d.name)

        companion object {
            suspend fun fromId(id: DeckId): Deck = Deck(deckId = id, name = withCol { decks.name(id) })
        }
    }

    /**
     * The name to be displayed to the user. Contains only
     * the sub-deck name rather than the entire deck name.
     * Eg: foo::bar -> bar
     */
    fun getDisplayName(context: Context) =
        when (this) {
            is Deck -> name.substringAfterLast("::")
            is AllDecks -> context.getString(R.string.card_browser_all_decks)
        }

    /**
     * The full name of the deck
     */
    fun getFullDisplayName(context: Context) =
        when (this) {
            is Deck -> name
            is AllDecks -> context.getString(R.string.card_browser_all_decks)
        }

    override fun toString() =
        when (this) {
            is Deck -> name
            is AllDecks -> "All Decks"
        }

    companion object {
        /**
         * @param includeFiltered Whether to include filtered decks in the output
         * @param skipEmptyDefault Whether to skip the empty default deck
         * @return all [SelectableDecks][SelectableDeck] in the collection satisfying the filter
         */
        suspend fun fromCollection(
            includeFiltered: Boolean,
            skipEmptyDefault: Boolean = true,
        ): List<Deck> =
            withCol {
                decks.allNamesAndIds(
                    includeFiltered = includeFiltered,
                    skipEmptyDefault = skipEmptyDefault,
                )
            }.map { nameAndId -> Deck(nameAndId) }
    }
}
