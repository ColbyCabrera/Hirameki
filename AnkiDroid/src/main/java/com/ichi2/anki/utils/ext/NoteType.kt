// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils.ext

import com.ichi2.anki.libanki.NotetypeJson

/**
 * Regular expression pattern for extracting cloze text fields.
 */
private val clozeRegex = "\\{\\{(?:.*?:)?cloze:([^}]*)\\}\\}".toRegex()

fun NotetypeJson.getAllClozeTextFields(): List<String> {
    if (!this.isCloze) {
        throw IllegalStateException("getAllClozeTextFields called on non-cloze template")
    }

    val questionFormat = templates.single().qfmt
    return clozeRegex.findAll(questionFormat).map { it.groups[1]!!.value }.toList()
}
