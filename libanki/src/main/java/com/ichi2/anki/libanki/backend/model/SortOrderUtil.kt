// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.libanki.backend.model

import anki.search.SortOrderKt.builtin
import anki.search.sortOrder
import com.ichi2.anki.libanki.SortOrder

// Conversion functions from SortOrder to anki.search.SortOrder

fun SortOrder.toProtoBuf() =
    sortOrder {
        when (this@toProtoBuf) {
            is SortOrder.NoOrdering -> {
                none = anki.generic.Empty.getDefaultInstance()
            }

            is SortOrder.AfterSqlOrderBy ->
                custom = customOrdering

            is SortOrder.BuiltinSortKind ->
                builtin =
                    builtin {
                        column = value
                        reverse = this@toProtoBuf.reverse
                    }

            else -> throw IllegalStateException(this.toString())
        }
    }
