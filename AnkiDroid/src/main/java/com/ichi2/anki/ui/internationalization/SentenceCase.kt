// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.ui.internationalization

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

// Functions for handling a move from 'Title Case' in Anki Desktop to 'Sentence case' in AnkiDroid

/**
 * Converts a string to sentence case if it matches the provided resource in `sentence-case.xml`
 *
 * ```
 * "Toggle Suspend".toSentenceCase(R.string.sentence_toggle_suspend) // "Toggle suspend"
 * ```
 */
fun String.toSentenceCase(
    context: Context,
    @StringRes resId: Int,
): String {
    val resString = context.getString(resId)
    // lowercase both for the comparison: sentence case doesn't mean all words are lowercase
    if (this.lowercase() == resString.lowercase()) return resString
    return this
}

fun String.toSentenceCase(
    fragment: Fragment,
    @StringRes resId: Int,
): String = toSentenceCase(fragment.requireContext(), resId)
