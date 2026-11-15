// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import kotlinx.serialization.Serializable

/** What the card page found where a touch started, see `hirameki.touchProbe` in `hirameki-card-page.js`. */
@Serializable
internal data class CardTouchProbe(
    val hasTextSelection: Boolean = false,
    /** On an element which handles taps itself, such as a link, a button or a click handler. */
    val isOnTapTarget: Boolean = false,
    /** On a tap target which also responds to dragging, such as an input or a video. */
    val isOnDragTarget: Boolean = false,
) {
    /** A touch with text selected ends the selection. */
    val claimsTaps: Boolean get() = hasTextSelection || isOnTapTarget

    /** A swipe never clicks, so only a drag target keeps it from the reviewer. */
    val claimsSwipes: Boolean get() = hasTextSelection || isOnDragTarget
}
