// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.AutoAdvanceSignal.MediaFinished
import com.ichi2.anki.reviewer.AutoAdvanceSignal.SideShown
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AutoAdvanceSignalHandlerTest {
    private val automaticAnswer =
        AutomaticAnswer(mock(), AutomaticAnswerSettings(AutomaticAnswerAction.BURY_CARD, 5.0, 5.0)).apply { enable() }
    private val handler = AutoAdvanceSignalHandler { automaticAnswer }

    private val hasTimer get() = automaticAnswer.timeoutHandler.hasMessages(0)

    @Test
    fun `a side that does not wait for media starts its timer when shown`() {
        handler.handle(SideShown(CardSide.QUESTION, waitsForMedia = false))
        assertTrue(hasTimer)
    }

    @Test
    fun `a side that waits for media starts its timer when its media finishes`() {
        handler.handle(SideShown(CardSide.ANSWER, waitsForMedia = true))
        assertFalse(hasTimer, "waiting for media")

        handler.handle(MediaFinished(CardSide.ANSWER))
        assertTrue(hasTimer)
    }

    @Test
    fun `media finishing for a side no longer shown is ignored`() {
        handler.handle(SideShown(CardSide.ANSWER, waitsForMedia = true))
        handler.handle(MediaFinished(CardSide.QUESTION))
        assertFalse(hasTimer)
    }

    @Test
    fun `showing a side cancels the previous side's timer`() {
        handler.handle(SideShown(CardSide.QUESTION, waitsForMedia = false))
        handler.handle(SideShown(CardSide.ANSWER, waitsForMedia = true))
        assertFalse(hasTimer)
    }
}
