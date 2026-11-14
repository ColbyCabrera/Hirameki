// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.testutil

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NoteEditorActivity
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes a block of code with the NoteEditor activity on the activity's main thread.
 * @param block The block of code to execute with the NoteEditor activity.
 * @throws Throwable if any exception is thrown during the execution of the block.
 */
@Throws(Throwable::class)
fun ActivityScenario<NoteEditorActivity>.onNoteEditor(block: (NoteEditorActivity) -> Unit) {
    val wrapped = AtomicReference<Throwable?>(null)
    this.onActivity { activity: NoteEditorActivity ->
        try {
            block(activity)
        } catch (t: Throwable) {
            wrapped.set(t)
        }
    }
    wrapped.get()?.let { throw it }
}
