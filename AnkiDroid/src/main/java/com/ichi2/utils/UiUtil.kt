// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import android.app.Dialog
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.ViewGroup
import android.widget.Spinner

object UiUtil {
    fun makeBold(s: String): Spannable {
        val str = SpannableStringBuilder(s)
        str.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return str
    }

    fun Spinner.setSelectedValue(value: Any?) {
        for (position in 0 until this.adapter.count) {
            if (this.adapter.getItem(position) != value) continue
            this.setSelection(position)
            return
        }
    }

    fun Dialog.makeFullscreen() {
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}
