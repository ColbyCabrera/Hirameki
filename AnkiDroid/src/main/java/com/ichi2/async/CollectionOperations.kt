// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Divyansh Kushwaha <kushwaha.divyansh.dxn@gmail.com>

package com.ichi2.async

import com.ichi2.anki.CardTemplateNotetype
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NotetypeJson
import timber.log.Timber

/**
 * Takes a list of media file names and removes them from the [Collection]
 * @param unused List of media names to be deleted
 */
fun deleteMedia(
    col: Collection,
    unused: List<String>,
): Int {
    col.media.trashFiles(unused)
    return unused.size
}

/**
 * Trashes the specified media files and immediately empties the trash in the given [Collection].
 *
 * @param col The [Collection] from which media files should be removed.
 * @param unused A list of media file names to be deleted.
 */
fun clearMediaAndTrash(
    col: Collection,
    unused: List<String>,
) {
    col.media.trashFiles(unused)
    col.media.emptyTrash()
}

/**
 * Handles everything for a note type change at once - template add / deletes as well as content updates
 */
@KotlinCleanup("strongly type templateChanges")
fun saveNoteType(
    col: Collection,
    notetype: NotetypeJson,
    templateChanges: ArrayList<Array<Any>>,
) {
    Timber.d("saveNoteType")
    val oldNoteType = col.notetypes.get(notetype.id)

    // TODO: make undoable
    val newTemplates = notetype.templates
    for (change in templateChanges) {
        val oldTemplates = oldNoteType!!.templates
        when (change[1] as CardTemplateNotetype.ChangeType) {
            CardTemplateNotetype.ChangeType.ADD -> {
                Timber.d("saveNoteType() adding template %s", change[0])
                col.notetypes.addTemplate(oldNoteType, newTemplates[change[0] as Int])
            }
            CardTemplateNotetype.ChangeType.DELETE -> {
                Timber.d("saveNoteType() deleting template currently at ordinal %s", change[0])
                col.notetypes.remTemplate(oldNoteType, oldTemplates[change[0] as Int])
            }
        }
    }

    // required for Rust: the modified time can't go backwards, and we updated the note type by adding fields
    // This could be done better
    notetype.mod = oldNoteType!!.mod
    col.notetypes.save(notetype)
    col.notetypes.update(notetype)
}
