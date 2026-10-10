/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.LibAnkiAlias

/**
 * strip off unicode isolation markers from a translated string for testing purposes
 */
@LibAnkiAlias("without_unicode_isolation")
fun withoutUnicodeIsolation(s: String): String = s.replace("\u2068", "").replace("\u2069", "")

@LibAnkiAlias("with_collapsed_whitespace")
fun withCollapsedWhitespace(s: String): String = s.replace(WHITESPACE_RUN, " ")

/**
 * Matches a run of one or more whitespace characters.
 *
 * `\p{IsWhite_Space}` rather than `\s`. Java's `\s` is ASCII-only
 * (`[ \t\n\x0B\f\r]`), so it misses U+00A0 no-break space and U+3000
 * ideographic space — the latter being routine in Japanese and Chinese text.
 * Python's `\s`, which upstream's `with_collapsed_whitespace` uses, *is*
 * Unicode-aware for `str` patterns, so plain `\s` here would silently diverge
 * from the behaviour this alias exists to mirror.
 *
 * Hoisted to a top-level `val` so the `Pattern` is built once: constructing
 * `Regex(...)` at the call site compiles a new `Pattern` every time. (Passing a
 * `Regex` *into* `replace` does not recompile it — only the construction costs.)
 */
private val WHITESPACE_RUN = Regex("\\p{IsWhite_Space}+")
