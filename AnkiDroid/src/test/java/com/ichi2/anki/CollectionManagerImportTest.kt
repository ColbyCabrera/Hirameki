// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.exception.CollectionLockedException
import net.ankiweb.rsdroid.BackendException.BackendImportException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class CollectionManagerImportTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
    }

    /**
     * A failed import must not replace the import error with a collection-open error.
     * If the original collection cannot be reopened, that failure is attached as suppressed.
     */
    @Test
    fun `failure to reopen does not replace the import error`() =
        runTest {
            addBasicNote()
            val backup = corruptDatabaseBackup()
            CollectionManager.emulatedOpenFailure = CollectionOpenFailure.LOCKED
            try {
                val failure = assertFailsWith<BackendImportException> { CollectionManager.importColpkg(backup.path) }

                assertIs<CollectionLockedException>(failure.suppressed.single())
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }

    /** A failed import must leave the original collection usable. */
    @Test
    fun `failed import reopens the original collection`() =
        runTest {
            val note = addBasicNote("original", "answer")
            val backup = corruptDatabaseBackup()

            assertFailsWith<BackendImportException> { CollectionManager.importColpkg(backup.path) }

            val fields = CollectionManager.withOpenColOrNull { getNote(note.id).fields.toList() }
            assertNotNull(fields, "The original collection should be open after the import fails")
            assertEquals(listOf("original", "answer"), fields)
        }

    private fun corruptDatabaseBackup(): File =
        tempFolder.newFile("corrupt.colpkg").apply {
            ZipOutputStream(outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("collection.anki2"))
                archive.write("not a SQLite database".toByteArray())
                archive.closeEntry()
            }
        }
}
