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
 * Deck-level interactions that are threaded from [DeckPickerScreen] down to each rendered deck row.
 *
 * Each callback receives the currently bound [DisplayDeckNode], which keeps row rendering code free
 * of navigation and view-model dependencies.
 */
data class DeckRowActions(
    val onDeckClick: (DisplayDeckNode) -> Unit,
    val onExpandClick: (DisplayDeckNode) -> Unit,
    val onDeckOptions: (DisplayDeckNode) -> Unit,
    val onRename: (DisplayDeckNode) -> Unit,
    val onCustomStudy: (DisplayDeckNode) -> Unit,
    val onUnbury: (DisplayDeckNode) -> Unit,
    val onExportDeck: (DisplayDeckNode) -> Unit,
    val onDelete: (DisplayDeckNode) -> Unit,
    val onRebuild: (DisplayDeckNode) -> Unit,
    val onEmpty: (DisplayDeckNode) -> Unit,
    val onCreateSubdeck: (DisplayDeckNode) -> Unit,
)

/**
 * Actions exposed through the top app bar overflow menu in the deck picker.
 */
data class MoreOptionsMenuActions(
    val onCheckDatabase: () -> Unit,
    val onExport: () -> Unit,
    val onDeleteEmptyCards: () -> Unit,
    val onManageNoteTypes: () -> Unit,
)

/**
 * Actions exposed through the expandable floating action button menu.
 *
 * These callbacks map to collection-level creation and import flows rather than deck-specific
 * actions.
 */
data class FabActions(
    val onAddNote: () -> Unit,
    val onAddDeck: () -> Unit,
    val onAddSharedDeck: () -> Unit,
    val onAddFilteredDeck: () -> Unit,
    val onImport: () -> Unit,
)
