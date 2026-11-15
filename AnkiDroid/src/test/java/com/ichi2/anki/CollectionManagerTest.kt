// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.CollectionOpenFailure
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.exception.CollectionLockedException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class CollectionManagerTest : RobolectricTest() {
    @Test
    fun isCollectionOpenFlow_updatesWhenCollectionStateChanges() =
        runTest {
            CollectionManager.ensureClosed()
            assertFalse(CollectionManager.isCollectionOpenFlow.value)

            CollectionManager.ensureOpen()
            assertTrue(CollectionManager.isCollectionOpenFlow.value)

            CollectionManager.ensureClosed()
            assertFalse(CollectionManager.isCollectionOpenFlow.value)
        }

    @Test
    fun isCollectionOpenFlow_updatesWhenSetColForTestsIsCalled() =
        runTest {
            CollectionManager.ensureOpen()
            val col = CollectionManager.getColUnsafe()

            CollectionManager.ensureClosed()
            assertFalse(CollectionManager.isCollectionOpenFlow.value)

            CollectionManager.setColForTests(col)
            assertTrue(CollectionManager.isCollectionOpenFlow.value)

            CollectionManager.setColForTests(null)
            assertFalse(CollectionManager.isCollectionOpenFlow.value)
        }

    @Test
    fun openingCollectionClearsDetectedCorruption() =
        runTest {
            CollectionManager.ensureClosed()
            DatabaseCorruption.onCorruptionDetected()
            assertTrue(DatabaseCorruption.isDetected)

            CollectionManager.ensureOpen()

            assertFalse(
                "a successful open should clear detected corruption",
                DatabaseCorruption.isDetected,
            )
        }

    @Test
    fun `ensureOpen with a locked collection throws CollectionLockedException`() =
        runTest {
            CollectionManager.ensureClosed()
            CollectionManager.emulatedOpenFailure = CollectionOpenFailure.LOCKED
            try {
                val failure = assertFailsWith<CollectionLockedException> { CollectionManager.ensureOpen() }
                assertIs<BackendDbLockedException>(failure.cause)
                assertEquals(
                    targetContext.getString(R.string.database_locked_summary_new, targetContext.getString(R.string.col_path)),
                    failure.message,
                )
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }
}
