// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

/*
 *  This file incorporates code under the following license
 *
 *   Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *   Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 *
 *   https://github.com/JetBrains/kotlin/blob/6ea9f879abd61ba9578b322eb07ff6a8450de11f/libraries/stdlib/common/src/generated/_Ranges.kt#L1476
 */

package com.ichi2.anki.common.utils.ext

/**
 * Ensures the number is within the provided range.
 *
 * If too small, [min] is returned
 * If too large, [max] is returned
 *
 * This does NOT validate that min < max for performance reasons
 */
fun Float.clamp(
    min: Float,
    max: Float,
): Float {
    if (this < min) return min
    if (this > max) return max
    return this
}
