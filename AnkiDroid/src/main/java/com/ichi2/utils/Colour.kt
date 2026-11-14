// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.utils

/**
 * [this] is `@ColorInt`
 * Return RRGGBB in hexadecimal
 */
fun Int.toRGBHex() = String.format("#%06X", 0xFFFFFF and this)
