// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

/*
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     Copyright (C) 2011 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *  https://android.googlesource.com/platform/tools/base/+/2856eb45fc34aff6c86ab8729d545c147dfd9c19/lint/libs/lint_checks/src/main/java/com/android/tools/lint/checks/StringFormatDetector.java
 */

package com.ichi2.anki.lint.utils

import org.w3c.dom.Node
import java.lang.StringBuilder

object StringFormatDetector {
    fun addText(
        sb: StringBuilder,
        node: Node,
    ) {
        val nodeType = node.nodeType
        if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            sb.append(stripQuotes(node.nodeValue.trim()))
        } else {
            val childNodes = node.childNodes
            var i = 0
            val n = childNodes.length
            while (i < n) {
                addText(sb, childNodes.item(i))
                i++
            }
        }
    }

    /**
     * Removes all the unescaped quotes. See [Escaping
 * apostrophes and quotes](http://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling)
     */
    fun stripQuotes(s: String): String {
        val sb = StringBuilder()
        var isEscaped = false
        var isQuotedBlock = false
        var i = 0
        val len = s.length
        while (i < len) {
            val current = s[i]
            if (isEscaped) {
                sb.append(current)
                isEscaped = false
            } else {
                isEscaped = current == '\\' // Next char will be escaped so we will just copy it
                if (current == '"') {
                    isQuotedBlock = !isQuotedBlock
                } else if (current == '\'') {
                    if (isQuotedBlock) {
                        // We only add single quotes when they are within a quoted block
                        sb.append(current)
                    }
                } else {
                    sb.append(current)
                }
            }
            i++
        }
        return sb.toString()
    }
}
