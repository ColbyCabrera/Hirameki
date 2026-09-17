/*
 *  Copyright (c) 2026 Hirameki contributors
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.compose.components

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import java.io.File

/**
 * every compose menu in the app goes through [MenuExitMotion].
 *
 * nothing makes a call site use the wrapper: a bare `DropdownMenu` or `ExposedDropdownMenu` compiles, and
 * closes by vanishing again. the difference is only visible in the alpha spring picked inside the popup, which a
 * test cannot reach in a production menu, so this reads the sources: each menu call has to be the direct
 * content of `MenuExitMotion(expanded = x) {` and pass that same `expanded = x`, since a wrapper fed a different
 * value flips its motion on the wrong pass (see [MenuExitMotion]).
 */
class MenuExitMotionCoverageTest {
    @Test
    fun `every compose menu closes through MenuExitMotion with its own expanded`() {
        // unit tests run in the module folder. every source set but the tests ships in some flavor
        val sources =
            File("src")
                .listFiles { dir -> dir.name != "test" && dir.name != "androidTest" }!!
                .flatMap { dir -> dir.walk().filter { it.extension == "kt" } }
        val menuCalls =
            sources.flatMap { file ->
                val text = file.readText()
                MENU_CALL
                    .findAll(text)
                    .filterNot { call -> isComment(text, call.range.first) }
                    .map { call -> MenuCall(file, text, call.range) }
            }
        // an empty scan (sources moved, the pattern broken) would otherwise pass
        assertThat("compose menus were found in the sources", menuCalls, not(empty()))

        val unwrapped = menuCalls.filterNot { it.isWrapped() }.map { it.location() }
        assertThat(
            "each DropdownMenu( / ExposedDropdownMenu( call has to sit directly inside " +
                "MenuExitMotion(expanded = x) { and pass the same expanded = x as its first argument",
            unwrapped,
            equalTo(emptyList()),
        )
    }

    private class MenuCall(
        val file: File,
        val text: String,
        val range: IntRange,
    ) {
        fun isWrapped(): Boolean {
            val wrapperExpanded =
                WRAPPER_BEFORE.find(text.substring(0, range.first))?.groupValues?.get(1)?.trim() ?: return false
            val menuExpanded = MENU_EXPANDED.find(text.substring(range.last + 1))?.groupValues?.get(1)?.trim()
            return menuExpanded == wrapperExpanded
        }

        fun location() = "${file.invariantSeparatorsPath}:${text.substring(0, range.first).count { it == '\n' } + 1}"
    }

    private companion object {
        /** a menu call: not DropdownMenuItem(, nor a longer name that happens to end in DropdownMenu( */
        val MENU_CALL = Regex("""(?<!\w)(?:Exposed)?DropdownMenu\(""")

        /** the wrapper opening right before the menu call, with nothing else between them */
        val WRAPPER_BEFORE = Regex("""MenuExitMotion\(expanded = ([^)]+)\)\s*\{\s*\z""")

        /** the menu's own expanded argument, passed first */
        val MENU_EXPANDED = Regex("""\A\s*expanded = ([^,)\n]+)""")

        fun isComment(
            text: String,
            offset: Int,
        ): Boolean {
            val line = text.substring(text.lastIndexOf('\n', offset) + 1, offset).trimStart()
            return line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")
        }
    }
}
