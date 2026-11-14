// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Danilo Mendes <danilodanicomendes@gmail.com>

package com.ichi2.anki.utils.ext

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

fun View.isTouchWithinBounds(event: MotionEvent): Boolean {
    val rect = Rect().apply { getDrawingRect(this) }
    return rect.contains((left + event.x).toInt(), (top + event.y).toInt())
}
