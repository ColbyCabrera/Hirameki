// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

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
