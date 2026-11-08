/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.reviewer.card

/** What the card page should be showing. */
data class CardDisplay(
    /** Changes each time a card is shown, even when the next card's HTML is identical. */
    val cardDisplayIndex: Long,
    val isAnswerShown: Boolean,
    val questionHtml: String,
    val answerHtml: String,
    val bodyClass: String,
    val baseUrl: String,
) {
    fun isSameCardAs(other: CardDisplay?): Boolean =
        other != null &&
            cardDisplayIndex == other.cardDisplayIndex &&
            questionHtml == other.questionHtml &&
            answerHtml == other.answerHtml
}
