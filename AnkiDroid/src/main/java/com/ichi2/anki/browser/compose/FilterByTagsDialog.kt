// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.browser.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.TagsDialog
import com.ichi2.anki.dialogs.compose.TagsState

@Composable
fun FilterByTagsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    allTags: TagsState,
    initialSelection: Set<String>,
    deckTags: Set<String> = emptySet(),
    initialFilterByDeck: Boolean = false,
    onFilterByDeckChanged: (Boolean) -> Unit = {},
) {
    TagsDialog(
        onDismissRequest = onDismissRequest,
        onConfirm = { checked, _ -> onConfirm(checked) },
        allTags = allTags,
        initialSelection = initialSelection,
        initialIndeterminate = emptySet(),
        deckTags = deckTags,
        initialFilterByDeck = initialFilterByDeck,
        onFilterByDeckChanged = onFilterByDeckChanged,
        title = stringResource(id = R.string.card_browser_search_by_tag),
        confirmButtonText = stringResource(id = R.string.dialog_ok),
        showFilterByDeckToggle = true,
    )
}
