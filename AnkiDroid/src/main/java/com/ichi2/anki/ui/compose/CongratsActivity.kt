// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.getColUnsafe
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.StudyOptionsComposeActivity
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import timber.log.Timber

class CongratsActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            val col = getColUnsafe()
            val timeUntilNextDay =
                (col.sched.dayCutoff * 1000 - TimeManager.time.intTimeMS()).coerceAtLeast(0L)

            setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
                when (CustomStudyAction.fromBundle(bundle)) {
                    CustomStudyAction.CUSTOM_STUDY_SESSION, CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                        openStudyOptionsAndFinish()
                    }
                }
            }

            setContent {
                CongratsScreen(
                    onNavigateUp = { finish() },
                    onDeckOptions = {
                        val intent = DeckOptions.getIntent(this, col.decks.current().id)
                        startActivity(intent)
                    },
                    onCustomStudy = {
                        val customStudy = CustomStudyDialog.createInstance(col.decks.current().id)
                        showDialogFragment(customStudy)
                    },
                    timeUntilNextDay = timeUntilNextDay,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting collection in CongratsActivity")
            finish()
        }
    }

    private fun openStudyOptionsAndFinish() {
        launchCatchingTask {
            val deckId = withCol { decks.selected() }
            val intent =
                Intent(this@CongratsActivity, StudyOptionsComposeActivity::class.java).apply {
                    putExtra(StudyOptionsComposeActivity.DECK_ID, deckId)
                }
            startActivity(intent)
            finish()
        }
    }
}
