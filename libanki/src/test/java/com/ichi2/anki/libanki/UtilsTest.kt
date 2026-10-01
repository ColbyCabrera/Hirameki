/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.libanki

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun testSplit() {
        val sep = Consts.FIELD_SEPARATOR
        assertEquals(listOf("foo", "bar"), Utils.splitFields("foo${sep}bar"))
        assertEquals(listOf("", "foo", "", "", ""), Utils.splitFields("${sep}foo${sep}${sep}$sep"))
    }

    @Test
    fun testIds2StrIntArray() {
        assertEquals("()", Utils.ids2str(null as IntArray?))
        assertEquals("()", Utils.ids2str(IntArray(0)))
        assertEquals("(1)", Utils.ids2str(intArrayOf(1)))
        assertEquals("(1, 2, 3)", Utils.ids2str(intArrayOf(1, 2, 3)))
    }

    @Test
    fun testIds2StrLongArray() {
        assertEquals("()", Utils.ids2str(null as LongArray?))
        assertEquals("()", Utils.ids2str(LongArray(0)))
        assertEquals("(1)", Utils.ids2str(longArrayOf(1L)))
        assertEquals("(1, 2, 3)", Utils.ids2str(longArrayOf(1L, 2L, 3L)))
    }
}
