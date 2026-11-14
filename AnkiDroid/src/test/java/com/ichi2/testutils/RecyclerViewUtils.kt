// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.testutils

import androidx.recyclerview.widget.RecyclerView

object RecyclerViewUtils {
    inline fun <reified VH : RecyclerView.ViewHolder?> viewHolderAt(
        recyclerView: RecyclerView,
        position: Int,
    ): VH = recyclerView.findViewHolderForAdapterPosition(position) as VH
}
