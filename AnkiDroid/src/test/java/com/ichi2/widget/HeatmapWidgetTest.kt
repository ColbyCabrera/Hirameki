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

package com.ichi2.widget

import android.database.Cursor
import androidx.glance.color.ColorProviders
import androidx.glance.unit.ColorProvider
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DB
import com.ichi2.anki.libanki.LibAnki
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.ankiweb.rsdroid.Backend
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeatmapWidgetTest {

    @Test
    fun testGetColorForCount() {
        val colors = mockk<ColorProviders>()
        val surfaceVariant = mockk<ColorProvider>()
        val primary = mockk<ColorProvider>()

        every { colors.surfaceVariant } returns surfaceVariant
        every { colors.primary } returns primary

        // 0 -> surfaceVariant, 0.5f
        var result = HeatmapWidget.getColorForCount(0, colors)
        assertEquals(surfaceVariant, result.first)
        assertEquals(0.5f, result.second, 0.01f)

        // 1 -> primary, 0.25f
        result = HeatmapWidget.getColorForCount(1, colors)
        assertEquals(primary, result.first)
        assertEquals(0.25f, result.second, 0.01f)

        // 5 -> primary, 0.25f
        result = HeatmapWidget.getColorForCount(5, colors)
        assertEquals(primary, result.first)
        assertEquals(0.25f, result.second, 0.01f)

        // 6 -> primary, 0.5f
        result = HeatmapWidget.getColorForCount(6, colors)
        assertEquals(primary, result.first)
        assertEquals(0.5f, result.second, 0.01f)

        // 20 -> primary, 0.5f
        result = HeatmapWidget.getColorForCount(20, colors)
        assertEquals(primary, result.first)
        assertEquals(0.5f, result.second, 0.01f)

        // 21 -> primary, 0.8f
        result = HeatmapWidget.getColorForCount(21, colors)
        assertEquals(primary, result.first)
        assertEquals(0.8f, result.second, 0.01f)

        // 40 -> primary, 0.8f
        result = HeatmapWidget.getColorForCount(40, colors)
        assertEquals(primary, result.first)
        assertEquals(0.8f, result.second, 0.01f)

        // 41 -> primary, 1f
        result = HeatmapWidget.getColorForCount(41, colors)
        assertEquals(primary, result.first)
        assertEquals(1f, result.second, 0.01f)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testFetchHeatmapData() = runTest {
        // Mock Cursor
        val mockCursor = mockk<Cursor>()
        // Simulate 2 rows:
        // 1. day=100, count=5
        // 2. day=101, count=10
        // moveToNext returns true twice, then false
        every { mockCursor.moveToNext() } returns true andThen true andThen false
        every { mockCursor.getLong(0) } returns 100L andThen 101L
        every { mockCursor.getInt(1) } returns 5 andThen 10
        every { mockCursor.close() } returns Unit

        // Mock DB
        val mockDb = mockk<DB> {
            every { query(any(), *anyVararg()) } returns mockCursor
            every { query(any()) } returns mockCursor
        }

        // Mock Collection
        val mockCol = mockk<Collection> {
            every { db } returns mockDb
            every { dbClosed } returns false
        }

        // Mock Backend to prevent loading native libraries
        val mockBackend = mockk<Backend>()
        setBackend(mockBackend)

        // Inject mock collection
        CollectionManager.setColForTests(mockCol)

        try {
            // Execute
            val result = HeatmapWidget.fetchHeatmapData()

            // Verify
            assertEquals(2, result.size)
            assertEquals(5, result[100L])
            assertEquals(10, result[101L])
        } finally {
            // Cleanup
            CollectionManager.setColForTests(null)
            setBackend(null)
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun setBackend(backend: Backend?) {
            LibAnki.backend = backend
        }
    }
}
