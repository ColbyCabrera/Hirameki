// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import anki.collection.OpChanges
import com.ichi2.anki.observability.ChangeManager
import timber.log.Timber
import kotlin.test.fail

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureNoOpsExecuted(block: suspend () -> Unit) {
    val subscription = ChangeCounter()
    ChangeManager.subscribe(subscription)
    block()
    if (!subscription.hasChanges) {
        Timber.d("ensureNoOpsExecuted: success")
        return
    }

    fail("ChangeManager should not be called; ${ChangeManager.subscriberCount} subscribers")
}

/**
 * Ensures no calls to [ChangeManager.notifySubscribers] via [undoableOp]
 */
suspend fun ensureOpsExecuted(
    count: Int,
    block: suspend () -> Unit,
) {
    val subscription = ChangeCounter()

    Timber.v("Listening for ChangeManager ops")
    ChangeManager.subscribe(subscription)
    block()
    if (subscription.changeCount == count) {
        Timber.d("ensureOpsExecuted: success")
        return
    }

    fail("ChangeManager: expected $count calls; ${ChangeManager.subscriberCount} subscribers")
}

// used to ensure a strong reference to the subscription is held
private class ChangeCounter : ChangeManager.Subscriber {
    private var changes = 0
    val changeCount get() = changes
    val hasChanges get() = changes > 0

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        Timber.d("ChangeManager op detected")
        this.changes++
    }
}
