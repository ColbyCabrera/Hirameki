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
