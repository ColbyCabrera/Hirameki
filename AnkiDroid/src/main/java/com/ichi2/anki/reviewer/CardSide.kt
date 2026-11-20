// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.reviewer

/**
 * @param int Used for serialisation
 */
enum class CardSide(
    val int: Int,
) {
    QUESTION(0),
    ANSWER(1),
    BOTH(2),
    ;

    /** Whether a binding for this side applies while [shownSide] ([QUESTION] or [ANSWER]) is displayed. */
    fun appliesTo(shownSide: CardSide): Boolean = this == BOTH || this == shownSide

    companion object {
        fun fromAnswer(displayingAnswer: Boolean): CardSide = if (displayingAnswer) ANSWER else QUESTION
    }
}
