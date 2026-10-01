/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.noteeditor

/** Responsible for recreating EditFieldLines after NoteEditor operations
 * This primarily exists so we can use saved instance state to repopulate the dynamically created FieldEditLine
 */
class FieldState private constructor() {
    /** How fields should be changed when the UI is rebuilt  */
    class FieldChangeType(
        val type: Type,
    )

    enum class Type {
        INIT,
        CLEAR_KEEP_STICKY,
        CHANGE_FIELD_COUNT,
        REFRESH,
        REFRESH_WITH_MAP,
    }

    companion object {
        private fun allowFieldRemapping(oldFields: Array<Array<String>>): Boolean = oldFields.size > 2
    }
}
