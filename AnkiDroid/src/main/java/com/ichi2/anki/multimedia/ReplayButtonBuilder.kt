/*
 * Copyright (c) 2026 the Anki-Android contributors
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

package com.ichi2.anki.multimedia

import com.ichi2.anki.common.utils.htmlEncode

object ReplayButtonBuilder {
    private const val DEFAULT_CONTENT_DESCRIPTION = "Replay audio"

    private const val REPLAY_BUTTON_TEMPLATE = """
        <a href="%s" class="%s" title="%s" aria-label="%s" role="button">
            <svg xmlns="http://www.w3.org/2000/svg" class="play-action" viewBox="256 -768 512 576" focusable="false" aria-hidden="true">
                <path fill="currentColor" d="M320-273v-414q0-17 12-28.5t28-11.5q5 0 10.5 1.5T381-721l326 207q9 6 13.5 15t4.5 19q0 10-4.5 19T707-446L381-239q-5 3-10.5 4.5T360-233q-16 0-28-11.5T320-273Z"/>
            </svg>
        </a>
    """

    fun createReplayButton(
        url: String,
        contentDescription: String = DEFAULT_CONTENT_DESCRIPTION,
        extraClasses: String = "",
    ): String {
        val classes =
            listOf("replay-button", extraClasses).filter { it.isNotBlank() }.joinToString(" ")
        val encodedUrl = url.htmlEncode()
        val encodedDescription = contentDescription.htmlEncode()
        return REPLAY_BUTTON_TEMPLATE.format(
            encodedUrl,
            classes,
            encodedDescription,
            encodedDescription
        )
    }
}