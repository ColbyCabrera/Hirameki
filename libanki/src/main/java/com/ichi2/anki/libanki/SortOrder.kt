// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.libanki

/** Helper class, libAnki uses a union
 *
 * https://github.com/david-allison-1/anki/blob/2296461136ada51437da076fda41cf735743c0e0/pylib/anki/collection.py#L417-L438
 *
 */
abstract class SortOrder {
    class NoOrdering : SortOrder()

    /** Based on config: sortType and sortBackwards */
    class UseCollectionOrdering : SortOrder()

    /** A custom SQL string placed after "order by" */
    class AfterSqlOrderBy(
        val customOrdering: String,
    ) : SortOrder()

    class BuiltinSortKind(
        val value: String,
        val reverse: Boolean,
    ) : SortOrder()
}
