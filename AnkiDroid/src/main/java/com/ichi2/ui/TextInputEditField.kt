// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.autofill.AutofillValue
import com.google.android.material.textfield.TextInputEditText

class TextInputEditField : TextInputEditText {
    private var autoFillListener: AutoFillListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun autofill(value: AutofillValue) {
        super.autofill(value)
        if (autoFillListener != null) {
            autoFillListener!!.onAutoFill(value)
        }
    }

    fun interface AutoFillListener {
        fun onAutoFill(value: AutofillValue)
    }

    fun setAutoFillListener(listener: AutoFillListener) {
        autoFillListener = listener
    }
}
