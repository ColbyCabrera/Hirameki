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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import kotlin.test.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The heatmap's day bucketing, against a real collection.
 *
 * [HeatmapWidgetTest] mocks the collection, so it can only check the SQL; these cases check that the counts
 * land in the day the scheduler credits each review to.
 */
@RunWith(AndroidJUnit4::class)
class HeatmapWidgetDayCutoffTest : RobolectricTest() {
    @Test
    fun `reviews are counted into the collection's day, not the UTC day`() =
        runTest {
            val cutoffMillis = col.sched.dayCutoff * 1000
            val hour = 3_600_000L
            val day = HeatmapWidget.DAY_IN_MILLIS

            // an hour before today rolls over, and a minute after it began: one anki day, whatever the
            // timezone puts between them. UTC-midnight bucketing split days here for everyone off UTC
            addReview(cutoffMillis - hour)
            addReview(cutoffMillis - day + 60_000)
            // two yesterday, none the day before, one three days back
            addReview(cutoffMillis - day - hour)
            addReview(cutoffMillis - day - 2 * hour)
            addReview(cutoffMillis - 3 * day - hour)

            val data = HeatmapWidget.fetchHeatmapData()

            assertEquals(2, data[0L])
            assertEquals(2, data[1L])
            assertNull(data[2L])
            assertEquals(1, data[3L])
        }

    /** a review at [idMillis]; revlog.id is the time it was answered, in milliseconds */
    private fun addReview(idMillis: Long) {
        col.db.execute(
            "INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) " +
                "VALUES (?, 1, -1, 3, 1, 1, 2500, 1000, 0)",
            idMillis,
        )
    }
}
