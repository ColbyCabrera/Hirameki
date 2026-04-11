/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.ui.compose.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import com.ichi2.anki.R

const val GET_SHARED_FAB_TAG = "expandable_fab_get_shared"
const val ADD_FILTERED_DECK_FAB_TAG = "expandable_fab_add_filtered_deck"
const val ADD_DECK_FAB_TAG = "expandable_fab_add_deck"
const val ADD_NOTE_FAB_TAG = "expandable_fab_add_note"
const val IMPORT_FAB_TAG = "expandable_fab_import"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddNote: () -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onImport: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    val onMenuItemClick = { action: () -> Unit ->
        {
            action()
            onExpandedChange(false)
        }
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            val fabMenuExpandedStateDescription = stringResource(R.string.fab_menu_expanded)
            val fabMenuCollapsedStateDescription = stringResource(R.string.fab_menu_collapsed)
            val fabMenuToggleContentDescription = stringResource(R.string.fab_menu_toggle)
            ToggleFloatingActionButton(modifier = Modifier
                .semantics {
                    traversalIndex = -1f
                    stateDescription =
                        if (expanded) fabMenuExpandedStateDescription else fabMenuCollapsedStateDescription
                    contentDescription = fabMenuToggleContentDescription
                }
                .focusRequester(focusRequester),
                checked = expanded,
                onCheckedChange = { onExpandedChange(it) }) {
                val fabIcon by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) R.drawable.close_24px else R.drawable.add_24px
                    }
                }
                Icon(
                    painter = painterResource(fabIcon),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            modifier = Modifier.testTag(IMPORT_FAB_TAG),
            onClick = onMenuItemClick(onImport),
            icon = {
                Icon(
                    painterResource(id = R.drawable.download_24px),
                    contentDescription = null,
                )
            },
            text = { Text(text = stringResource(R.string.menu_import)) },
        )
        FloatingActionButtonMenuItem(
            modifier = Modifier.testTag(GET_SHARED_FAB_TAG),
            onClick = onMenuItemClick(onAddSharedDeck),
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            text = { Text(text = stringResource(R.string.get_shared)) },
        )
        FloatingActionButtonMenuItem(
            modifier = Modifier.testTag(ADD_FILTERED_DECK_FAB_TAG),
            onClick = onMenuItemClick(onAddFilteredDeck),
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_add_filtered_deck), contentDescription = null
                )
            },
            text = { Text(text = stringResource(R.string.new_dynamic_deck)) },
        )
        FloatingActionButtonMenuItem(
            modifier = Modifier.testTag(ADD_DECK_FAB_TAG),
            onClick = onMenuItemClick(onAddDeck),
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_add_deck_filled), contentDescription = null
                )
            },
            text = { Text(text = stringResource(R.string.new_deck)) },
        )
        FloatingActionButtonMenuItem(
            modifier = Modifier.testTag(ADD_NOTE_FAB_TAG),
            onClick = onMenuItemClick(onAddNote),
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_add_note), contentDescription = null
                )
            },
            text = { Text(text = stringResource(R.string.add_card)) },
        )
    }
}