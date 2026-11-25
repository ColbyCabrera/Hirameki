// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024, 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.ui.compose.theme.appColorScheme
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` is added
 * * `bridgeCommand()` is ignored
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 * @param themeColors see [themeColorVariables]
 */
@Language("HTML")
fun stdHtml(
    context: Context = AnkiDroidApp.instance,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
    themeColors: Map<String, String> = themeColorVariables(appColorScheme(context, nightMode)),
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    val baseTheme = if (nightMode) "dark" else "light"
    val docClass = if (nightMode) "night-mode" else ""

    val colors = rootColorsCss(nightMode, themeColors)

    val jsAssets: List<String> =
        listOf(
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
    themeColors: Map<String, String>,
): String {
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""
    val themeColorDeclarations = themeColors.entries.joinToString(" ") { (name, value) -> "$name: $value;" }
    return ":root$rootNightMode { $themeColorDeclarations }"
}

/**
 * The app's colors for card pages: Anki's `--canvas` and `--fg`, and
 * `--ankidroid-color-*` for templates, such as `var(--ankidroid-color-primary)`.
 */
fun themeColorVariables(colors: ColorScheme): Map<String, String> =
    listOf(
        "--canvas" to colors.surface,
        "--fg" to colors.onSurface,
        "--ankidroid-color-primary" to colors.primary,
        "--ankidroid-color-on-primary" to colors.onPrimary,
        "--ankidroid-color-secondary" to colors.secondary,
        "--ankidroid-color-on-secondary" to colors.onSecondary,
        "--ankidroid-color-surface" to colors.surface,
        "--ankidroid-color-on-surface" to colors.onSurface,
        "--ankidroid-color-background" to colors.background,
        "--ankidroid-color-on-background" to colors.onBackground,
    ).associate { (name, color) -> name to color.toArgb().toRGBHex() }

/** @return body classes used when showing a card */
fun bodyClassForCardOrd(
    cardOrd: Int,
    nightMode: Boolean = Themes.currentTheme.isNightMode,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)}"

internal val NIGHT_MODE_BODY_CLASSES = listOf("nightMode", "night_mode")

private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String =
    if (nightMode) NIGHT_MODE_BODY_CLASSES.joinToString(" ") else ""
