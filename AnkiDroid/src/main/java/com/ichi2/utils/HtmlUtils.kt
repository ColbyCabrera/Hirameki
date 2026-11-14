// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import com.ichi2.anki.common.utils.htmlEncode

object HtmlUtils {
    // #5188 - compat.fromHtml converts newlines into spaces.
    fun convertNewlinesToHtml(html: String?): String? {
        if (html == null) {
            return null
        }
        val withoutWindowsLineEndings = html.replace("\r\n", "<br/>")
        // replace unix line endings
        return withoutWindowsLineEndings.replace("\n", "<br/>")
    }

    fun escape(html: String): String = html.htmlEncode()
}
