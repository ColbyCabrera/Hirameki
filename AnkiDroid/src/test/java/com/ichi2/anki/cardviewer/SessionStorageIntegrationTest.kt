// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.cardviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.previewer.stdHtml
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SessionStorageIntegrationTest : RobolectricTest() {
    @Test
    fun productionShellTemplatesIncludePolyfill() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cardTemplateContent =
            context.assets
                .open("card_template.html")
                .reader()
                .use { it.readText() }
        assertTrue(
            cardTemplateContent.contains("scripts/session_storage_polyfill.js"),
            "card_template.html must include polyfill",
        )

        val stdHtmlOutput = stdHtml(context)
        assertTrue(
            stdHtmlOutput.contains("scripts/session_storage_polyfill.js"),
            "stdHtml must include polyfill",
        )
    }
}
