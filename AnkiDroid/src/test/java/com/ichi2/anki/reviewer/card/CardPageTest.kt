// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import com.ichi2.anki.settings.Prefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test

class CardPageTest {
    private val question =
        CardDisplay(
            cardDisplayIndex = 1,
            isAnswerShown = false,
            questionHtml = "<p>Q</p>",
            answerHtml = "<p>A</p>",
            bodyClass = "card card1",
            baseUrl = "http://127.0.0.1:1/",
        )

    private val style =
        CardPageStyle(
            isNightMode = false,
            surfaceColor = "#111111",
            onSurfaceColor = "#222222",
            surfaceContainerColor = "#333333",
            primaryColor = "#444444",
            outlineColor = "#555555",
            question = CardSideStyle(45f, 52f, 0f, 500, 36),
            answer = CardSideStyle(22f, 28f, 0f, 400, 40),
            toolbarHeightPx = 80,
            hiramekiCssMode = Prefs.HIRAMEKI_CSS_ALL,
        )

    @Test
    fun `the question passes the answer for preloading, and the question side class`() {
        assertThat(
            CardPage.showScript(question, isNightMode = false, token = 7),
            equalTo("""_showQuestion("<p>Q</p>", "<p>A</p>", "card card1 hirameki-question"); hirameki.reportWhenShown(7);"""),
        )
    }

    @Test
    fun `the answer passes the answer side class`() {
        assertThat(
            CardPage.showScript(question.copy(isAnswerShown = true), isNightMode = false, token = 8),
            equalTo("""_showAnswer("<p>A</p>", "card card1 hirameki-answer"); hirameki.reportWhenShown(8);"""),
        )
    }

    @Test
    fun `html containing quotes and script tags stays one string`() {
        val html = """<script>let a = "b";</script>"""

        assertThat(
            CardPage.showScript(question.copy(questionHtml = html), isNightMode = false, token = 1),
            containsString(""""<script>let a = \"b\";</script>""""),
        )
    }

    @Test
    fun `night mode comes from the page's theme, not the card`() {
        assertThat(
            CardPage.showScript(question, isNightMode = true, token = 1),
            containsString(""""card card1 hirameki-question nightMode night_mode""""),
        )
    }

    @Test
    fun `a theme change updates the page in place and keeps the template's classes`() {
        val script = CardPage.styleUpdateScript(style.copy(isNightMode = true))

        assertThat(script, containsString("""documentElement.classList.toggle("night-mode", true)"""))
        assertThat(script, not(containsString("className")))
        assertThat(script, containsString("""classList.toggle("nightMode", true)"""))
        assertThat(script, containsString("""classList.toggle("night_mode", true)"""))
    }

    @Test
    fun `side rules keep the specificity of body card so templates can override them`() {
        assertThat(style.css(), containsString("body.card:where(.hirameki-question)"))
        assertThat(style.css(), containsString("body.card:where(.hirameki-answer)"))
    }

    @Test
    fun `no-font-size mode leaves font sizes to the template`() {
        val css = style.copy(hiramekiCssMode = Prefs.HIRAMEKI_CSS_NO_FONT_SIZE).css()

        assertThat(css, not(containsString("font-size: 45.0px")))
        assertThat(css, containsString("padding-top: 36px"))
    }

    @Test
    fun `disabled mode adds no css`() {
        assertThat(style.copy(hiramekiCssMode = Prefs.HIRAMEKI_CSS_DISABLED).css(), equalTo(""))
    }

    @Test
    fun `the same card seen again is a different card`() {
        assertThat(question.isSameCardAs(question.copy(cardDisplayIndex = 2)), equalTo(false))
    }

    @Test
    fun `flipping to the answer is the same card`() {
        assertThat(question.isSameCardAs(question.copy(isAnswerShown = true)), equalTo(true))
    }

    @Test
    fun `an edited card is a different card`() {
        assertThat(question.isSameCardAs(question.copy(questionHtml = "<p>Edited</p>")), equalTo(false))
    }
}
