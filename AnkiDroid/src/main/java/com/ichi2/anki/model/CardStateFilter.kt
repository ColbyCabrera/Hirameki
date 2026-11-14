// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.model

/**
 * Allows filtering a search by the state of cards
 *
 * @see [anki.search.SearchNode.CardState]
 */
enum class CardStateFilter {
    ALL_CARDS,
    NEW,
    DUE,
    ;

    val toSearch: String
        get() =
            when (this) {
                ALL_CARDS -> ""
                NEW -> "is:new "
                DUE -> "is:due "
            }
}
