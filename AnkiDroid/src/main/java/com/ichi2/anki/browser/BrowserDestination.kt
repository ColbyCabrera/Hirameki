// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.Destination

/**
 * Opens the [CardBrowser]
 */
class BrowserDestination(
    val deckId: DeckId,
) : Destination {
    override fun toIntent(context: Context): Intent {
        AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository.lastDeckId = deckId
        return Intent(context, CardBrowser::class.java)
    }
}
