// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.reviewer

import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AnswerButtons.AGAIN
import com.ichi2.anki.reviewer.AnswerButtons.EASY
import com.ichi2.anki.reviewer.AnswerButtons.GOOD
import com.ichi2.anki.reviewer.AnswerButtons.HARD
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AnswerButtonsTest {
    @Test
    fun checkButtons() {
        assertThat(AGAIN.toViewerCommand(), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE1))
        assertThat(HARD.toViewerCommand(), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE2))
        assertThat(GOOD.toViewerCommand(), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE3))
        assertThat(EASY.toViewerCommand(), equalTo(ViewerCommand.FLIP_OR_ANSWER_EASE4))
    }
}
