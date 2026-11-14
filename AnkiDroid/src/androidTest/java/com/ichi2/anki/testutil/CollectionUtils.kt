// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.testutil

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note

// todo: duplicated & used in unit tests
fun Collection.addNote(note: Note): Int {
    addNote(note, note.notetype.did)
    return note.numberOfCards(this)
}
