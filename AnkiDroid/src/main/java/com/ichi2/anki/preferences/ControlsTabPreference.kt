// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.R

class ControlsTabPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
        defStyleRes: Int = androidx.preference.R.style.Preference,
    ) : Preference(context, attrs, defStyleAttr, defStyleRes) {
        init {
            layoutResource = R.layout.controls_tab_layout
        }

        private var tabLayout: TabLayout? = null
        private var onTabSelectedListener: TabLayout.OnTabSelectedListener? = null

        fun setOnTabSelectedListener(listener: TabLayout.OnTabSelectedListener) {
            onTabSelectedListener?.let { oldListener ->
                tabLayout?.removeOnTabSelectedListener(oldListener)
            }
            onTabSelectedListener = listener
            tabLayout?.addOnTabSelectedListener(listener)
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            tabLayout = holder.itemView as? TabLayout
            onTabSelectedListener?.let { listener ->
                tabLayout?.removeOnTabSelectedListener(listener)
                tabLayout?.addOnTabSelectedListener(listener)
            }
        }
    }
