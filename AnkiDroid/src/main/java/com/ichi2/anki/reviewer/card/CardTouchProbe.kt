// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import kotlinx.serialization.Serializable

/** What the card page found where a touch started, see `hirameki.touchProbe` in `hirameki-card-page.js`. */
@Serializable
internal data class CardTouchProbe(
    val hasTextSelection: Boolean = false,
    /** On an element which handles touches itself, such as a link, a button or an input. */
    val isOnInteractiveElement: Boolean = false,
) {
    /** A touch with text selected ends the selection. */
    val belongsToCard: Boolean get() = hasTextSelection || isOnInteractiveElement
}
