/*
 * Copyright (c) 2025 The AnkiDroid Open Source Team
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.widgets

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.ichi2.anki.common.utils.annotation.KotlinCleanup

/**
 * An ImageButton that clears its tooltip and content description when detached from the window.
 * This is a workaround for a known Android memory leak where pending tooltip runnables
 * are not correctly removed, retaining the View and thus the Activity.
 *
 * See https://issuetracker.google.com/issues/143646422
 */
@KotlinCleanup("remove when minSdk >= 34? assuming it's fixed in later versions")
class SafeImageButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.appcompat.R.attr.imageButtonStyle,
    ) : AppCompatImageButton(context, attrs, defStyleAttr) {
        private var savedContentDescription: CharSequence? = null
        private var savedTooltipText: CharSequence? = null

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            // Workaround for memory leak: View.TooltipInfo.HideTooltipRunnable
            // If a tooltip was triggered (e.g. by long press on a view with contentDescription),
            // a runnable is posted to hide it. If the view is detached before the runnable runs,
            // it might not be removed, leaking the View and Context.
            // Clearing contentDescription (and tooltipText) forces the tooltip to hide and removes the runnable.
            if (contentDescription != null) {
                savedContentDescription = contentDescription
                contentDescription = null
            }
            tooltipText = null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (contentDescription == null && savedContentDescription != null) {
                contentDescription = savedContentDescription
                savedContentDescription = null
            }
        }
    }
