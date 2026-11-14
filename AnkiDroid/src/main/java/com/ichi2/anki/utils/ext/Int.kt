// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.

/*
 *  This file incorporates code under the following license
 *
 *   Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *   Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 *
 *   https://github.com/JetBrains/kotlin/blob/6ea9f879abd61ba9578b322eb07ff6a8450de11f/libraries/stdlib/common/src/generated/_Ranges.kt#L1447
 */

package com.ichi2.anki.utils.ext

inline fun Int.ifNotZero(block: (value: Int) -> Unit) {
    if (this == 0) return
    block(this)
}

/**
 * Ensures the number is within the provided range.
 *
 * If too small, [minimumValue] is returned
 * If too large, [maximumValue] is returned
 *
 * Similar to [coerceIn], but does NOT validate that min < max for performance reasons
 */
fun Int.clamp(
    minimumValue: Int,
    maximumValue: Int,
): Int {
    if (this < minimumValue) return minimumValue
    if (this > maximumValue) return maximumValue
    return this
}
