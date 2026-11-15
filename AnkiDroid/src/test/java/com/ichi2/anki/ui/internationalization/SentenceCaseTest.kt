// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.ui.internationalization

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SentenceCaseTest : RobolectricTest() {
    @Test
    fun `English is converted to sentence case`() {
        ensureCollectionLoadIsSynchronous()
        with(super.startRegularActivity<IntroductionActivity>()) {
            assertThat(TR.browsingToggleSuspend().toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
            assertThat(TR.browsingToggleBury().toSentenceCase(this, R.string.sentence_toggle_bury), equalTo("Toggle bury"))
            assertThat(TR.actionsSetDueDate().toSentenceCase(this, R.string.sentence_set_due_date), equalTo("Set due date"))
            assertThat(TR.actionsCustomStudy().toSentenceCase(this, R.string.sentence_custom_study), equalTo("Custom study"))
            assertThat(TR.emptyCardsWindowTitle().toSentenceCase(this, R.string.sentence_empty_cards), equalTo("Empty cards"))
            assertThat(TR.mediaCheckEmptyTrash().toSentenceCase(this, R.string.sentence_empty_trash), equalTo("Empty trash"))
            assertThat(TR.mediaCheckRestoreTrash().toSentenceCase(this, R.string.sentence_restore_deleted), equalTo("Restore deleted"))
            assertThat(TR.actionsGradeNow().toSentenceCase(this, R.string.sentence_grade_now), equalTo("Grade now"))

            assertThat("syncMediaLogTitle", TR.syncMediaLogTitle(), equalTo("Media Sync Log"))
            assertThat(
                "sentence_sync_media_log",
                TR.syncMediaLogTitle().toSentenceCase(this, R.string.sentence_sync_media_log),
                equalTo("Media sync log"),
            )

            assertThat("Toggle Suspend".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Toggle suspend"))
            assertThat("Ook? Ook?".toSentenceCase(this, R.string.sentence_toggle_suspend), equalTo("Ook? Ook?"))
        }
    }
}
