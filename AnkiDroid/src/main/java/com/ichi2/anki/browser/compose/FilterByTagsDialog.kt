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
        onAddTag = { /* Not used in this context */ },
    )
}
