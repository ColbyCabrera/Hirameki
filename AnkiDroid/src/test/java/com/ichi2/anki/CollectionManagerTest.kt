/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionManagerTest : RobolectricTest() {

    @Test
    fun isCollectionOpenFlow_updatesWhenCollectionStateChanges() = runTest {
        CollectionManager.ensureClosed()
        assertFalse(CollectionManager.isCollectionOpenFlow.value)

        CollectionManager.ensureOpen()
        assertTrue(CollectionManager.isCollectionOpenFlow.value)

        CollectionManager.ensureClosed()
        assertFalse(CollectionManager.isCollectionOpenFlow.value)
    }

    @Test
    fun isCollectionOpenFlow_updatesWhenSetColForTestsIsCalled() = runTest {
        CollectionManager.ensureOpen()
        val col = CollectionManager.getColUnsafe()

        CollectionManager.ensureClosed()
        assertFalse(CollectionManager.isCollectionOpenFlow.value)

        CollectionManager.setColForTests(col)
        assertTrue(CollectionManager.isCollectionOpenFlow.value)

        CollectionManager.setColForTests(null)
        assertFalse(CollectionManager.isCollectionOpenFlow.value)
    }
}
