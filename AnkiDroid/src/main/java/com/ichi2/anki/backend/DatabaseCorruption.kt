/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
