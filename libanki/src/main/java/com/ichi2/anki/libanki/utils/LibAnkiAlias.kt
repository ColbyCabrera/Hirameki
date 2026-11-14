// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.libanki.utils

/**
 * Specifies the name of the method
 * in anki's [pylib](https://github.com/ankitects/anki/tree/main/pylib/anki)
 */
@Retention(AnnotationRetention.SOURCE)
annotation class LibAnkiAlias(
    @Suppress("unused") val alias: String,
)
