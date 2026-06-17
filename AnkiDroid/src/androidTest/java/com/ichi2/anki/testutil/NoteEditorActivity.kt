/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
