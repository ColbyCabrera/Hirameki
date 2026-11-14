// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.backend

/**
 * Global state which distinguishes between an inaccessible and a corrupt **collection** database.
 *
 * Only corruption of the collection may be recorded here: [com.ichi2.anki.dialogs.DatabaseErrorDialog]
 * uses this to tell the user that their collection needs repairing. Corruption of an auxiliary
 * database (for example [com.ichi2.anki.MetaDB]) must not set this flag.
 *
 * @see isDetected
 */
object DatabaseCorruption {
    /**
     * `true` if collection database corruption was encountered during this process.
     *
     * Cleared by [clear], as a collection which opens successfully is no longer corrupt
     * (the user may have repaired it, or switched to a different collection).
     */
    var isDetected = false
        private set

    /** Records that the collection database is corrupt. */
    fun onCorruptionDetected() {
        isDetected = true
    }

    /**
     * Clears any detected corruption.
     *
     * Called once a collection has been opened successfully: the failure is no longer
     * actionable, as the user has either repaired the collection or selected another one.
     */
    fun clear() {
        isDetected = false
    }
}
