// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
