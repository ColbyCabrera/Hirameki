/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.reviewer

/**
 * Builds HTML content for card display.
 */
object CardHtmlBuilder {
    private const val PLAY_BUTTON_TEMPLATE = """
                <a href="%s" class="replay-button" title="%s" aria-label="Play %s" role="button">
                    <svg xmlns="http://www.w3.org/2000/svg" height="56px" width="56px" class="play-action" viewBox="0 -960 960 960">
                        <path d="M320-273v-414q0-17 12-28.5t28-11.5q5 0 10.5 1.5T381-721l326 207q9 6 13.5 15t4.5 19q0 10-4.5 19T707-446L381-239q-5 3-10.5 4.5T360-233q-16 0-28-11.5T320-273Z"/>
                    </svg>
                </a>
            """

    fun createPlayButton(url: String, content: String): String =
        PLAY_BUTTON_TEMPLATE.format(url, content, content)

    fun wrapWithStyles(html: String, css: String): String = "<style>$css</style>$html"
}
