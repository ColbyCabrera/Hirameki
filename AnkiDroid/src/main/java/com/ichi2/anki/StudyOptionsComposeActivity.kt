// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.deckpicker.compose.StudyOptionsData
import com.ichi2.anki.deckpicker.compose.StudyOptionsScreen
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class StudyOptionsComposeActivity : AnkiActivity() {
    @VisibleForTesting
    internal var collectionDispatcher: CoroutineDispatcher = ioDispatcher

    private var refreshCounter by mutableIntStateOf(0)

    private val reviewerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS ||
                result.resultCode == DeckPicker.RESULT_DB_ERROR ||
                result.resultCode == DeckPicker.RESULT_MEDIA_EJECTED
            ) {
                setResult(result.resultCode)
                finish()
            } else {
                refreshCounter++
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)

        setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION -> {
                    launchCatchingTask {
                        val deckId = withCol { decks.selected() }
                        val intent =
                            Intent(
                                this@StudyOptionsComposeActivity,
                                StudyOptionsComposeActivity::class.java,
                            ).apply {
                                putExtra(DECK_ID, deckId)
                            }
                        startActivity(intent)
                        finish()
                    }
                }

                CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                    refreshCounter++
                }
            }
        }

        setContent {
            var studyOptionsData by remember { mutableStateOf<StudyOptionsData?>(null) }

            LaunchedEffect(refreshCounter) {
                studyOptionsData =
                    withContext(collectionDispatcher) {
                        withCol {
                            val deckId = intent.getLongExtra(DECK_ID, decks.current().id)
                            decks.select(deckId)
                            val deck = decks.current()
                            val counts = sched.counts()
                            var buriedNew = 0
                            var buriedLearning = 0
                            var buriedReview = 0
                            val tree = sched.deckDueTree(deck.id)
                            if (tree != null) {
                                buriedNew = tree.newCount - counts.new
                                buriedLearning = tree.learnCount - counts.lrn
                                buriedReview = tree.reviewCount - counts.rev
                            }
                            StudyOptionsData(
                                deckId = deck.id,
                                deckName = deck.getString("name"),
                                deckDescription = deck.description,
                                newCount = counts.new,
                                lrnCount = counts.lrn,
                                revCount = counts.rev,
                                buriedNew = buriedNew,
                                buriedLrn = buriedLearning,
                                buriedRev = buriedReview,
                                totalNewCards = sched.totalNewForCurrentDeck(),
                                totalCards = decks.cardCount(deck.id, includeSubdecks = true),
                                isFiltered = deck.isFiltered,
                                haveBuried = sched.haveBuried(),
                            )
                        }
                    }
            }

            AnkiDroidTheme {
                Scaffold { innerPadding ->
                    StudyOptionsScreen(
                        modifier =
                            Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                        studyOptionsData = studyOptionsData,
                        onStartStudy = {
                            reviewerLauncher.launch(Reviewer.getIntent(this))
                        },
                        onCustomStudy = { deckId ->
                            showDialogFragment(CustomStudyDialog.createInstance(deckId))
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val DECK_ID = "deck_id"
    }
}
