// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 David Allison <62114487+david-allison@users.noreply.github.com>

package com.ichi2.anki.observability

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Wraps a routine that returns OpChanges* or similar undo info,
 * notifying [ChangeManager] subscribers of the changes.
 *
 * Once [block] returns, subscribers are notified even if the caller is cancelled.
 * Cancellation before [block] starts still prevents the operation from running.
 */
suspend fun <T : Any> undoableOp(
    handler: Any? = null,
    block: Collection.() -> T,
): T {
    var changes: T? = null
    try {
        return withCol {
            // Capture the result before dispatching back to the caller, which may be cancelled.
            block().also { changes = it }
        }
    } finally {
        changes?.let {
            withContext(NonCancellable + Dispatchers.Main) {
                ChangeManager.notifySubscribers(it, handler)
            }
        }
    }
}
