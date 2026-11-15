// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer

/**
 * Builds HTML content for card display.
 */
object CardHtmlBuilder {
    fun wrapWithStyles(
        html: String,
        css: String,
    ): String = "<style>$css</style>$html"
}
