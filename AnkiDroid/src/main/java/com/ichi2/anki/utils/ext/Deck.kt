// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils.ext

import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks

fun Decks.update(
    did: DeckId,
    block: Deck.() -> Unit,
) {
    val deck = getLegacy(did)!!
    block(deck)
    this.save(deck)
}
