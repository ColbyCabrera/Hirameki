// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.pages

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
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
    val currentContext by rememberUpdatedState(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        viewModel.snackbarMessage
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .collect { messageResId ->
                showThemedToast(currentContext, messageResId, true)
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
                    showAllDecksOption = false,
                )
            }
        },
    )
}
