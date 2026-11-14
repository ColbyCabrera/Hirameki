// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024, 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import android.content.Context
import androidx.appcompat.widget.ThemeUtils
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.LanguageUtils
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` is added
 * * `bridgeCommand()` is ignored
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 */
@Language("HTML")
fun stdHtml(
    context: Context = AnkiDroidApp.instance,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    val baseTheme = if (nightMode) "dark" else "light"
    val docClass = if (nightMode) "night-mode" else ""

    val colors = rootColorsCss(nightMode) { attr -> ThemeUtils.getThemeAttrColor(context, attr) }

    val jsAssets: List<String> =
        listOf(
            "scripts/session_storage_polyfill.js",
            "backend/js/jquery.min.js",
            "backend/js/mathjax.js",
            "backend/js/vendor/mathjax/tex-chtml-full.js",
            "backend/js/reviewer.js",
        ) + extraJsAssets
    val jsTxt =
        jsAssets.joinToString("\n") {
            """<script src="file:///android_asset/$it"></script>"""
        }

    return """
        <!DOCTYPE html>
        <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
        <head>
            <title>AnkiDroid</title>
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/root-vars.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/ankidroid.css">
            <style>
                .night-mode button { --canvas: #606060; --fg: #eee; }
                $colors
            </style>
        </head>
        <body class="${bodyClass()}">
            <div id="qa"></div>
            $jsTxt
            <script>bridgeCommand = function(){};</script>
        </body>
        </html>
        """.trimIndent()
}

internal fun rootColorsCss(
    nightMode: Boolean,
    resolveColor: (attr: Int) -> Int,
): String {
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""
    val canvasColor = resolveColor(android.R.attr.colorBackground).toRGBHex()
    val fgColor = resolveColor(android.R.attr.textColor).toRGBHex()
    val themeColors =
        listOf(
            "primary" to AppCompatR.attr.colorPrimary,
            "on-primary" to MaterialR.attr.colorOnPrimary,
            "secondary" to MaterialR.attr.colorSecondary,
            "on-secondary" to MaterialR.attr.colorOnSecondary,
            "surface" to MaterialR.attr.colorSurface,
            "on-surface" to MaterialR.attr.colorOnSurface,
            "background" to android.R.attr.colorBackground,
            "on-background" to MaterialR.attr.colorOnBackground,
        ).joinToString(" ") { (name, attr) ->
            "--ankidroid-color-$name: ${resolveColor(attr).toRGBHex()};"
        }
    return ":root$rootNightMode { --canvas: $canvasColor; --fg: $fgColor; $themeColors }"
}

/** @return body classes used when showing a card */
fun bodyClassForCardOrd(
    cardOrd: Int,
    nightMode: Boolean = Themes.currentTheme.isNightMode,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)}"

internal val NIGHT_MODE_BODY_CLASSES = listOf("nightMode", "night_mode")

private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String =
    if (nightMode) NIGHT_MODE_BODY_CLASSES.joinToString(" ") else ""
