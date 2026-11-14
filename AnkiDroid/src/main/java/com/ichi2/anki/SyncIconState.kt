// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ankitects Pty Ltd <https://apps.ankiweb.net>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

/**
 * The state of the sync icon in the toolbar.
 *
 * This is used to show a badge when the collection is "dirty" (i.e. has [PendingChanges]).
 *
 * @see DeckPicker.updateSyncIconFromState
 * @see com.ichi2.anki.deckpicker.DeckPickerViewModel.fetchSyncIconState
 */
enum class SyncIconState {
    /** No changes to sync. */
    Normal,

    /** The collection has been modified, but not synced. */
    PendingChanges,

    /** A one-way sync is recommended. */
    OneWay,

    /** The user is not logged in. */
    NotLoggedIn,
}
