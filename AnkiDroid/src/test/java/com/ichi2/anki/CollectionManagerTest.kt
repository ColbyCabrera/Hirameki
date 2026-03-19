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
        // Assume RobolectricTest opens the collection by default, or we can explicitly open it
        CollectionManager.ensureOpen()
        assertTrue(CollectionManager.isCollectionOpenFlow.value)

        // Close the collection
        CollectionManager.ensureClosed()
        assertFalse(CollectionManager.isCollectionOpenFlow.value)
    }
}
