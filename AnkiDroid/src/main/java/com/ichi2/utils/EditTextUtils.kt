// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.widget.EditText

/** Moves the cursor to the end of the [EditText] */
fun EditText.moveCursorToEnd() = setSelection(text?.length ?: 0)

/**
 * Parses the [text][EditText.text] as an [Int] and returns the result, or `null` if the
 * string is not a valid representation of an [Int].
 *
 * note: "1.0" returns `null`
 */
fun EditText.textAsIntOrNull() = this.text?.textAsIntOrNull()

fun CharSequence.textAsIntOrNull() = this.toString().toIntOrNull()
