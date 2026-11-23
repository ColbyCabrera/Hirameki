// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.previewer

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewerHelpersTest {
    private val themeColors = mapOf("--ankidroid-color-primary" to "#000003")

    @Test
    fun `day mode targets plain root`() {
        val css = rootColorsCss(nightMode = false, "#000001", "#000002", themeColors)
        assertTrue(css.startsWith(":root {"), css)
    }

    @Test
    fun `night mode targets night-mode root`() {
        val css = rootColorsCss(nightMode = true, "#000001", "#000002", themeColors)
        assertTrue(css.startsWith(":root[class*=night-mode] {"), css)
    }

    @Test
    fun `root declares the canvas, foreground and theme colors`() {
        val css = rootColorsCss(nightMode = false, "#000001", "#000002", themeColors)

        assertEquals(
            mapOf("--canvas" to "#000001", "--fg" to "#000002", "--ankidroid-color-primary" to "#000003"),
            parseVariables(css),
        )
    }

    @Test
    fun `theme color variables come from the matching color scheme roles`() {
        val colors =
            lightColorScheme(
                primary = Color(0xFF000003),
                onPrimary = Color(0xFF000004),
                secondary = Color(0xFF000005),
                onSecondary = Color(0xFF000006),
                surface = Color(0xFF000009),
                onSurface = Color(0xFF00000A),
                background = Color(0xFF000001),
                onBackground = Color(0xFF00000B),
            )

        assertEquals(
            mapOf(
                "--ankidroid-color-primary" to "#000003",
                "--ankidroid-color-on-primary" to "#000004",
                "--ankidroid-color-secondary" to "#000005",
                "--ankidroid-color-on-secondary" to "#000006",
                "--ankidroid-color-surface" to "#000009",
                "--ankidroid-color-on-surface" to "#00000A",
                "--ankidroid-color-background" to "#000001",
                "--ankidroid-color-on-background" to "#00000B",
            ),
            themeColorVariables(colors),
        )
    }

    private fun parseVariables(css: String): Map<String, String> =
        Regex("""(--[\w-]+):\s*(#[0-9A-F]{6});""")
            .findAll(css)
            .associate { it.groupValues[1] to it.groupValues[2] }
}
