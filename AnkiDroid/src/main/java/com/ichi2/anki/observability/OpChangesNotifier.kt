// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.observability

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wrap a routine that returns OpChanges* or similar undo info with this
 * to notify change subscribers of the changes. */
suspend fun <T : Any> undoableOp(
    handler: Any? = null,
    block: Collection.() -> T,
): T =
    withCol {
        block()
    }.also {
        withContext(Dispatchers.Main) {
            ChangeManager.notifySubscribers(it, handler)
        }
    }
