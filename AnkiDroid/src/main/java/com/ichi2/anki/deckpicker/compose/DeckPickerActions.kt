/*
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.8@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.deckpicker.compose

import com.ichi2.anki.deckpicker.DisplayDeckNode

/**
 * Actions for deck row items (context menu, click, expand).
 * These traverse the entire composable tree from [DeckPickerScreen] down to [DeckItem].
 */
data class DeckRowActions(
    val onDeckClick: (DisplayDeckNode) -> Unit,
    val onExpandClick: (DisplayDeckNode) -> Unit,
    val onDeckOptions: (DisplayDeckNode) -> Unit,
    val onRename: (DisplayDeckNode) -> Unit,
    val onExport: (DisplayDeckNode) -> Unit,
    val onDelete: (DisplayDeckNode) -> Unit,
    val onRebuild: (DisplayDeckNode) -> Unit,
    val onEmpty: (DisplayDeckNode) -> Unit,
    val onCreateSubdeck: (DisplayDeckNode) -> Unit,
)

/**
 * Actions for the StudyOptions panel (tablet only) and its top bar overflow menu.
 */
data class StudyOptionsPanelActions(
    val onStartStudy: () -> Unit,
    val onRebuildDeck: (Long) -> Unit,
    val onEmptyDeck: (Long) -> Unit,
    val onCustomStudy: (Long) -> Unit,
    val onDeckOptionsItemSelected: (Long) -> Unit,
    val onUnbury: (Long) -> Unit,
)

/**
 * Actions for the floating action button menu.
 */
data class FabActions(
    val onAddNote: () -> Unit,
    val onAddDeck: () -> Unit,
    val onAddSharedDeck: () -> Unit,
    val onAddFilteredDeck: () -> Unit,
    val onCheckDatabase: () -> Unit,
)
