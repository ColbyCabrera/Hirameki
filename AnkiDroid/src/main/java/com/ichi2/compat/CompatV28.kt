// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.compat

import android.os.Parcel

// writeBoolean requires API level 29
fun Parcel.writeBooleanCompat(value: Boolean) {
    writeByte(if (value) 1 else 0)
}

// readBoolean requires API level 29
fun Parcel.readBooleanCompat() = readByte() != 0.toByte()
