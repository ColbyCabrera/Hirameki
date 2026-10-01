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
package com.ichi2.anki.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.BrowserOptions
import com.ichi2.anki.model.CardsOrNotes

@Composable
fun BrowserOptionsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (CardsOrNotes, Boolean, Boolean) -> Unit,
    initialCardsOrNotes: CardsOrNotes,
    initialIsTruncated: Boolean,
    initialShouldIgnoreAccents: Boolean,
    onManageColumnsClicked: () -> Unit,
    onRenameFlagClicked: () -> Unit,
) {
    var cardsOrNotes by remember { mutableStateOf(initialCardsOrNotes) }
    var isTruncated by remember { mutableStateOf(initialIsTruncated) }
    var shouldIgnoreAccents by remember { mutableStateOf(initialShouldIgnoreAccents) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.browser_options_dialog_heading)) },
        text = {
            BrowserOptions(
                onCardsModeSelected = { cardsOrNotes = CardsOrNotes.CARDS },
                onNotesModeSelected = { cardsOrNotes = CardsOrNotes.NOTES },
                initialMode = if (cardsOrNotes == CardsOrNotes.CARDS) 0 else 1,
                onTruncateChanged = { isTruncated = it },
                initialTruncate = isTruncated,
                onIgnoreAccentsChanged = { shouldIgnoreAccents = it },
                initialIgnoreAccents = shouldIgnoreAccents,
                onManageColumnsClicked = onManageColumnsClicked,
                onRenameFlagClicked = onRenameFlagClicked,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(cardsOrNotes, isTruncated, shouldIgnoreAccents)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
