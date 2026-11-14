// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 the Anki-Android contributors
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.multimedia

import com.ichi2.anki.common.utils.htmlEncode
import org.jetbrains.annotations.VisibleForTesting

object ReplayButtonBuilder {
    @VisibleForTesting
    internal const val TEST_DEFAULT_CONTENT_DESCRIPTION = "Replay audio"

    fun createReplayButton(
        url: String,
        contentDescription: String,
        extraClasses: String = "",
    ): String {
        val classes =
            buildList {
                add("replay-button")
                addAll(
                    extraClasses
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .map { it.htmlEncode() },
                )
            }.joinToString(" ")
        val encodedUrl = url.htmlEncode()
        val encodedDescription = contentDescription.htmlEncode()
        return """
            <a href="$encodedUrl" class="$classes" title="$encodedDescription" aria-label="$encodedDescription" role="button">
                <svg xmlns="http://www.w3.org/2000/svg" class="play-action" viewBox="256 -768 512 576" focusable="false" aria-hidden="true">
                    <path fill="currentColor" d="M320-273v-414q0-17 12-28.5t28-11.5q5 0 10.5 1.5T381-721l326 207q9 6 13.5 15t4.5 19q0 10-4.5 19T707-446L381-239q-5 3-10.5 4.5T360-233q-16 0-28-11.5T320-273Z"/>
                </svg>
            </a>
            """.trimIndent()
    }
}
