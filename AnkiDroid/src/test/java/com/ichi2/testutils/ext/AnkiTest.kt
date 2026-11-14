// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils.ext

import anki.collection.OpChangesWithCount
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.observability.undoableOp

suspend fun AnkiTest.addBasicNoteWithOp(
    fields: List<String> = listOf("foo", "bar"),
    noteType: NotetypeJson = col.notetypes.byName("Basic")!!,
): Note =
    col.newNote(noteType).also { note ->
        for ((i, field) in fields.withIndex()) {
            note.setField(i, field)
        }
        undoableOp<OpChangesWithCount> { col.addNote(note, Consts.DEFAULT_DECK_ID) }
    }
