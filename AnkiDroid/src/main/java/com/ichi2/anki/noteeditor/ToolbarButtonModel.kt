// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.noteeditor

/**
 * Represents a button in the note editor toolbar.
 *
 * When the button is clicked, it inserts [prefix] before the current selection
 * and [suffix] after the current selection in the text field.
 */
data class ToolbarButtonModel(
    /** The position/order of the button in the toolbar */
    val index: Int,
    /** The label displayed on the button */
    val text: String,
    /** The text to insert before the current selection. Defaults to empty string. */
    val prefix: String = "",
    /** The text to insert after the current selection. Defaults to empty string. */
    val suffix: String = "",
)
