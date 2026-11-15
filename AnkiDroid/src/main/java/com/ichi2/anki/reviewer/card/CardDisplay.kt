// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

/** What the card page should be showing. */
data class CardDisplay(
    /** Changes each time a card is shown, even when the next card's HTML is identical. */
    val cardDisplayIndex: Long,
    val isAnswerShown: Boolean,
    val questionHtml: String,
    val answerHtml: String,
    /** Without the night mode classes, which follow the theme of the page rather than the card. */
    val bodyClass: String,
    val baseUrl: String,
) {
    fun isSameCardAs(other: CardDisplay?): Boolean =
        other != null &&
            cardDisplayIndex == other.cardDisplayIndex &&
            questionHtml == other.questionHtml &&
            answerHtml == other.answerHtml
}
