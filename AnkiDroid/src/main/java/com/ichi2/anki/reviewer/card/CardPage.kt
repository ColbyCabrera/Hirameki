// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import android.content.Context
import com.ichi2.anki.previewer.NIGHT_MODE_BODY_CLASSES
import com.ichi2.anki.previewer.stdHtml
import kotlinx.serialization.json.Json

/** The contract with the card page: its HTML, and the scripts which change what it shows. */
internal object CardPage {
    const val QUESTION_BODY_CLASS = "hirameki-question"
    const val ANSWER_BODY_CLASS = "hirameki-answer"

    /** The object the page reports through, see `hirameki-card-page.js`. */
    const val BRIDGE_NAME = "hiramekiCardBridge"

    private val EXTRA_JS_ASSETS = listOf("backend/js/reviewer_extras_bundle.js", "scripts/hirameki-card-page.js")
    private const val STYLE_ELEMENT_ID = "compose-styles"
    private const val EXTRAS_CSS_LINK =
        """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""

    fun shellHtml(
        context: Context,
        style: CardPageStyle,
    ): String {
        val styleElement = """<style id="$STYLE_ELEMENT_ID">${style.css()}</style>"""
        return stdHtml(context, EXTRA_JS_ASSETS, style.isNightMode)
            .replace("</head>", "$EXTRAS_CSS_LINK\n$styleElement\n</head>")
    }

    fun showScript(
        display: CardDisplay,
        isNightMode: Boolean,
        token: Int,
    ): String {
        val sideClass = if (display.isAnswerShown) ANSWER_BODY_CLASS else QUESTION_BODY_CLASS
        val themeClasses = if (isNightMode) NIGHT_MODE_BODY_CLASSES.joinToString(" ", prefix = " ") else ""
        val classes = jsString("${display.bodyClass} $sideClass$themeClasses")
        val answer = jsString(display.answerHtml)
        val show =
            if (display.isAnswerShown) {
                "_showAnswer($answer, $classes);"
            } else {
                "_showQuestion(${jsString(display.questionHtml)}, $answer, $classes);"
            }
        return "$show hirameki.reportWhenShown($token);"
    }

    /** Holds later cards in the page's queue until the visual viewport is at [pageScale]. */
    fun awaitZoomResetScript(pageScale: Float): String = "hirameki.awaitZoomReset($pageScale);"

    /** Runs after any queued card has been shown. A throw must not reject the queue, which would stop every later card. */
    fun commandScript(script: String): String =
        "_queueAction(() => { try { (0, eval)(${jsString(script)}); } catch (error) { console.error(error); } });"

    fun styleUpdateScript(style: CardPageStyle): String {
        val theme = if (style.isNightMode) "dark" else "light"
        val bodyClassToggles =
            NIGHT_MODE_BODY_CLASSES.joinToString(" ") {
                "document.body.classList.toggle(${jsString(it)}, ${style.isNightMode});"
            }
        return """
            document.documentElement.classList.toggle("night-mode", ${style.isNightMode});
            document.documentElement.setAttribute("data-bs-theme", ${jsString(theme)});
            document.getElementById(${jsString(STYLE_ELEMENT_ID)}).textContent = ${jsString(style.css())};
            $bodyClassToggles
            """.trimIndent()
    }

    private fun jsString(value: String) = Json.encodeToString(value)
}
