// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R

/**
 * Compose wrapper for the Deck Options page.
 *
 * Uses [PageWebView] to display the Anki deck options UI.
 *
 * @param deckId The ID of the deck to configure
 * @param onNavigateUp Callback for back navigation
 */
@Composable
fun DeckOptionsScreen(
    deckId: Long,
    onNavigateUp: () -> Unit,
) {
    PageWebView(
        path = "deck-options/$deckId",
        title = stringResource(R.string.menu__deck_options),
        onNavigateUp = onNavigateUp,
    )
}
