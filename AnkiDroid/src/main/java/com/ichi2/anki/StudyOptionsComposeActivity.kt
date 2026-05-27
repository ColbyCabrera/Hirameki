/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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
package com.ichi2.anki

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.ichi2.anki.CollectionManager.withCol
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)

        setContent {
            var studyOptionsData by remember { mutableStateOf<StudyOptionsData?>(null) }
            var refreshCounter by remember { mutableIntStateOf(0) }

            setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
                when (CustomStudyAction.fromBundle(bundle)) {
                    CustomStudyAction.CUSTOM_STUDY_SESSION -> finish()
                    CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                        refreshCounter++
                    }
                }
            }

            LaunchedEffect(refreshCounter) {
                studyOptionsData = withContext(collectionDispatcher) {
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
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        studyOptionsData = studyOptionsData,
                        onStartStudy = {
                            startActivity(Reviewer.getIntent(this))
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
