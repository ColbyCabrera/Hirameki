// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesAfterUndo
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.canProperlyShowSnackbars
import com.ichi2.anki.snackbar.showSnackbar

private suspend fun getUndoResultMessage(): String {
    val changes =
        undoableOp {
            if (!undoAvailable()) {
                OpChangesAfterUndo.getDefaultInstance()
            } else {
                undo()
            }
        }
    return if (changes.operation.isEmpty()) {
        TR.actionsNothingToUndo()
    } else {
        TR.undoActionUndone(changes.operation)
    }
}

private fun FragmentActivity.showUndoSnackbarMessage(
    message: String,
    duration: Int,
) {
    when {
        this is SnackbarForwarder -> forwardSnackbar(message)
        canProperlyShowSnackbars() -> showSnackbar(message, duration)
        else -> showThemedToast(this, message, false)
    }
}

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun FragmentActivity.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val message = getUndoResultMessage()
        showUndoSnackbarMessage(message, duration)
    }
}

suspend fun undoAndGetSnackbarMessage(): String = getUndoResultMessage()

suspend fun FragmentActivity.redoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val changes =
            undoableOp {
                if (redoAvailable()) {
                    redo()
                } else {
                    OpChangesAfterUndo.getDefaultInstance()
                }
            }
        val message =
            if (changes.operation.isEmpty()) {
                TR.actionsNothingToRedo()
            } else {
                TR.undoActionRedone(changes.operation)
            }
        showUndoSnackbarMessage(message, duration)
    }
}
