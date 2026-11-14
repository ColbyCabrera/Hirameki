// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

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
