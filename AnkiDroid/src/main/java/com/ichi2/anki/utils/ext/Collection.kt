// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils.ext

import androidx.annotation.CheckResult
import anki.collection.OpChangesWithCount
import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.Collection

/** Change the flag color of the specified cards. */
@CheckResult
fun Collection.setUserFlagForCards(
    cids: Iterable<Long>,
    flag: Flag,
): OpChangesWithCount = setUserFlagForCards(cids, flag.code)
