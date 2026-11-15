// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils.ext

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView

/** @see View.findViewById */
fun <T : View> RecyclerView.ViewHolder.findViewById(
    @IdRes id: Int,
) = itemView.findViewById<T>(id)
