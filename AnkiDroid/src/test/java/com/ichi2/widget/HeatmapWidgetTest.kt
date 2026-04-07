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
import kotlinx.coroutines.test.runTest
import net.ankiweb.rsdroid.Backend
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeatmapWidgetTest {

    @Test
    fun testGetColorForCount() {
        val colors = mock<ColorProviders>()
        val surfaceVariant = mock<ColorProvider>()
        val primary = mock<ColorProvider>()

        whenever(colors.surfaceVariant).thenReturn(surfaceVariant)
        whenever(colors.primary).thenReturn(primary)

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
        val mockCursor = mock<Cursor>()
        // Simulate 2 rows:
        // 1. day=100, count=5
        // 2. day=101, count=10
        // moveToNext returns true twice, then false
        whenever(mockCursor.moveToNext()).doReturn(true).doReturn(true).doReturn(false)
        whenever(mockCursor.getLong(0)).doReturn(100L).doReturn(101L)
        whenever(mockCursor.getInt(1)).doReturn(5).doReturn(10)

        // Mock DB
        val mockDb = mock<DB> {
            on { query(any(), any()) } doReturn mockCursor
            on { query(any()) } doReturn mockCursor
        }

        // Mock Collection
        val mockCol = mock<Collection> {
            on { db } doReturn mockDb
        }

        // Mock Backend to prevent loading native libraries
        val mockBackend = mock<Backend>()
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
