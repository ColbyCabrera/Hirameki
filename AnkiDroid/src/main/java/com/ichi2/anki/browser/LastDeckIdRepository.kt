// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.browser

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks

interface LastDeckIdRepository {
    var lastDeckId: DeckId?
}

/**
 * Saves the last selected [DeckId] in the Card Browser
 *
 * This exists as the old code used [PERSISTENT_STATE_FILE], rather than [AnkiDroidApp.sharedPrefs]
 *
 * [Decks.select] is not used in the Card Browser: this can be launched from a review session and
 * should not affect the session
 */
class SharedPreferencesLastDeckIdRepository : LastDeckIdRepository {
    override var lastDeckId: DeckId?
        get() =
            AnkiDroidApp.instance
                .getSharedPreferences(PERSISTENT_STATE_FILE, 0)
                .getLong(LAST_DECK_ID_KEY, Decks.NOT_FOUND_DECK_ID)
                .takeUnless { it == Decks.NOT_FOUND_DECK_ID }
        set(value) =
            if (value == null) {
                clearLastDeckId()
            } else {
                AnkiDroidApp.instance.getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit {
                    putLong(LAST_DECK_ID_KEY, value)
                }
            }

    companion object {
        fun clearLastDeckId() {
            val context: Context = AnkiDroidApp.instance
            context.getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit {
                remove(LAST_DECK_ID_KEY)
            }
        }

        private const val PERSISTENT_STATE_FILE = "DeckPickerState"
        private const val LAST_DECK_ID_KEY = "lastDeckId"
    }
}
