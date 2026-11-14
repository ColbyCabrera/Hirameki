// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.ichi2.anki.R

/**
 * Custom component that represents a resizable divider used in multi-pane layouts.
 * Encapsulates the resizing divider layout for better reusability.
 */
class ResizingDivider
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        private val dividerHandle: View

        init {
            // Inflate the original layout into this custom view
            LayoutInflater.from(context).inflate(R.layout.resizing_divider_internal, this, true)

            // Get reference to the divider handle
            dividerHandle = findViewById(R.id.divider_handle)

            // Set the default background color
            setBackgroundColor(context.getColor(R.color.idle_divider_color))
        }
    }
