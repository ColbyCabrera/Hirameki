// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewer.card

import com.ichi2.anki.settings.Prefs

/** The text style of one side of a card. */
data class CardSideStyle(
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val letterSpacingPx: Float,
    val fontWeight: Int,
    val paddingTopPx: Int,
)

/** Hirameki's styling of the card page. Colours are `#RRGGBB`. */
data class CardPageStyle(
    val isNightMode: Boolean,
    val surfaceColor: String,
    val onSurfaceColor: String,
    val surfaceContainerColor: String,
    val primaryColor: String,
    val outlineColor: String,
    val question: CardSideStyle,
    val answer: CardSideStyle,
    val toolbarHeightPx: Int,
    val hiramekiCssMode: String,
) {
    /** `:where` keeps these rules at the specificity of `body.card`, which templates may override. */
    private fun sideCss(
        sideClass: String,
        side: CardSideStyle,
    ): String {
        val fontSizeRules =
            if (hiramekiCssMode == Prefs.HIRAMEKI_CSS_NO_FONT_SIZE) {
                ""
            } else {
                """
                font-size: ${side.fontSizePx}px;
                line-height: ${side.lineHeightPx}px;
                letter-spacing: ${side.letterSpacingPx}px;
                """.trimIndent()
            }
        return """
            body.card:where(.$sideClass) {
                $fontSizeRules
                font-weight: ${side.fontWeight};
                padding-top: ${side.paddingTopPx}px;
            }
            """.trimIndent()
    }

    fun css(): String {
        if (hiramekiCssMode == Prefs.HIRAMEKI_CSS_DISABLED) return ""
        return """
            @import url('https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,100..900;1,100..900&display=swap');
            html {
                color: ${onSurfaceColor}EF;
                background-color: $surfaceColor;
            }
            body.card {
                text-align: center;
                font-family: "Roboto", sans-serif;
                text-wrap: pretty;
                padding-bottom: ${toolbarHeightPx}px;
                margin-left: 10px;
                margin-right: 10px;
                background-color: $surfaceColor;
                color: ${onSurfaceColor}EF;
            }
            ${sideCss(CardPage.QUESTION_BODY_CLASS, question)}
            ${sideCss(CardPage.ANSWER_BODY_CLASS, answer)}
            body.card .back {
                font-weight: 400;
                line-height: 1.4;
            }
            body.card.nightMode, body.card.night_mode {
                background-color: $surfaceColor;
                color: ${onSurfaceColor}EF;
            }
            hr {
                opacity: 0.1;
                margin: 12px 0px;
            }
            img {
                border-radius: 16px;
            }
            button {
                font-family: inherit;
                font-size: 14px;
                font-weight: 500;
                color: $onSurfaceColor;
                background-color: $surfaceContainerColor;
                border: 1px solid ${outlineColor}40;
                border-radius: 12px;
                padding: 2px 6px;
                cursor: pointer;
                transition: background-color 0.2s, box-shadow 0.2s, transform 0.1s;
                align-items: center;
                justify-content: center;
                min-height: 48px;
                box-shadow: 0 1px 2px rgba(0,0,0,0.05);
            }
            button:hover {
                background-color: ${surfaceContainerColor}D9;
                box-shadow: 0 4px 8px rgba(0,0,0,0.1);
            }
            button:active {
                background-color: ${surfaceContainerColor}B3;
                transform: scale(0.97);
            }
            button:focus {
                outline: 2px solid $primaryColor;
                outline-offset: 2px;
            }
            button:disabled {
                opacity: 0.45;
                cursor: not-allowed;
                transform: none;
            }

            body.card .replay-button {
                --replay-button-size: 42px;
                --replay-button-icon-color: $onSurfaceColor;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                margin: 8px;
                border-radius: 8px;
                width: var(--replay-button-size);
                height: var(--replay-button-size);
                color: var(--replay-button-icon-color);
                text-decoration: none;
                cursor: pointer;
                transition: transform 0.1s, opacity 0.2s;
                -webkit-tap-highlight-color: transparent;
            }
            body.card .replay-button:hover {
                opacity: 0.85;
            }
            body.card .replay-button:active {
                opacity: 0.7;
                transform: scale(0.97);
            }
            body.card .replay-button:focus-visible {
                outline: 2px solid $primaryColor;
                outline-offset: 2px;
            }
            body.card .replay-button .play-action {
                display: block;
                width: 100%;
                height: 100%;
                color: inherit;
                fill: currentColor;
            }
            body.card .replay-button .play-action path {
                fill: currentColor;
            }
            """.trimIndent()
    }
}
