/*
 *  Copyright (c) Colby Cabrera <colbycabrera.wd@gmail.com>
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
package com.ichi2.anki.pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.ui.compose.components.DeckSelector

/**
 * Compose wrapper for the Statistics (graphs) page.
 *
 * Uses [PageWebView] to display the Anki statistics graphs.
 */
@Composable
fun StatisticsScreen(
    onNavigateUp: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(),
) {
    val selectedDeck by viewModel.selectedDeck.collectAsStateWithLifecycle()
    val availableDecks by viewModel.availableDecks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { messageResId ->
            showThemedToast(context, messageResId, true)
        }
    }

    PageWebView(
        path = "graphs",
        title = null,
        onNavigateUp = onNavigateUp,
        jsCommands = viewModel.jsInjectionEvent,
        topBarActions = {
            Row(modifier = Modifier.widthIn(max = 300.dp)) {
                DeckSelector(
                    selectedDeck = selectedDeck,
                    availableDecks = availableDecks,
                    onDeckSelected = { viewModel.selectDeck(it) },
                    showAllDecksOption = false
                )
            }
        })
}
