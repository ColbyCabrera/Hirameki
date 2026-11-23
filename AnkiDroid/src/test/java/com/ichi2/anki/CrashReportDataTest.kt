// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.CrashReportData.Companion.isDeckNotFoundInLimitsMapException
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.libanki.Note
import com.ichi2.testutils.JvmTest
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportDataTest : JvmTest() {
    @Test
    fun `isDeckNotFoundInLimitsMapException test`() {
        addDeck("A::B::C")
        addBasicNote().apply { moveToDeck("A::B::C") }
        val parentDeckId = col.decks.idForName("A::B")!!
        // Drop A::B without removing A::B::C, leaving it without an entry in the limits map.
        col.db.execute("delete from decks where id = ?", parentDeckId)
        col.decks.select(col.decks.idForName("A")!!)

        val ex = assertFailsWith<Exception> { col.sched.counts() }
        assertTrue(ex.isDeckNotFoundInLimitsMapException())
    }

    @Test
    fun `missing notetype offers a database check`() {
        val exception = assertFailsWith<BackendNotFoundException> { Note.fromNotetypeId(col, -1) }

        assertEquals(HelpAction.OpenCheckDatabase, CrashReportData(exception, "test", true).helpAction)
    }

    @Test
    fun `missing notetype is not reported as a crash`() {
        val exception = assertFailsWith<BackendNotFoundException> { Note.fromNotetypeId(col, -1) }

        assertFalse(CrashReportData(exception, "test", true).shouldReportException())
    }

    @Test
    fun `other backend errors are still reported`() {
        assertTrue(CrashReportData(BackendException("unexpected error"), "test", true).shouldReportException())
    }
}
