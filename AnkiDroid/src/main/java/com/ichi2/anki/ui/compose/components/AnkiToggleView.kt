/***************************************************************************************
 * Copyright (c) 2026 Colby Cabrera <colbycabrera@gmail.com>                            *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.ui.compose.components

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.Checkable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A Checkable View that wraps the Compose [AnkiToggle].
 * This is used to interop with XML-based layouts and Preferences.
 */
class AnkiToggleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr), Checkable {

    private var isCheckedState by mutableStateOf(false)
    private var isEnabledState by mutableStateOf(true)
    private var onCheckedChangeListener: ((AnkiToggleView, Boolean) -> Unit)? = null

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun setChecked(checked: Boolean) {
        if (isCheckedState != checked) {
            isCheckedState = checked
            onCheckedChangeListener?.invoke(this, checked)
        }
    }

    override fun isChecked(): Boolean = isCheckedState

    override fun toggle() {
        isChecked = !isCheckedState
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isEnabledState = enabled
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).also {
            it.isChecked = isCheckedState
            it.isEnabled = isEnabledState
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        super.onRestoreInstanceState(state.superState)
        isCheckedState = state.isChecked
        isEnabledState = state.isEnabled
        super.setEnabled(state.isEnabled)
    }

    fun setOnCheckedChangeListener(listener: ((AnkiToggleView, Boolean) -> Unit)?) {
        onCheckedChangeListener = listener
    }

    @Composable
    override fun Content() {
        AnkiDroidTheme {
            val interactionSource = remember { MutableInteractionSource() }
            AnkiToggle(
                checked = isCheckedState, onCheckedChange = { newChecked ->
                    if (onCheckedChangeListener != null) {
                        isChecked = newChecked
                        performClick()
                    } else if (hasOnClickListeners()) {
                        isChecked = newChecked
                        performClick()
                    } else {
                        isChecked = newChecked
                        if (!triggerParentClick()) {
                            performClick()
                        }
                    }
                }, interactionSource = interactionSource, enabled = isEnabledState
            )
        }
    }

    private fun triggerParentClick(): Boolean {
        var current: android.view.ViewParent? = parent
        while (current is View) {
            if (current.hasOnClickListeners()) {
                return current.performClick()
            }
            current = current.parent
        }
        return false
    }

    private class SavedState : BaseSavedState {
        var isChecked = false
        var isEnabled = true

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            isChecked = source.readInt() != 0
            isEnabled = source.readInt() != 0
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (isChecked) 1 else 0)
            out.writeInt(if (isEnabled) 1 else 0)
        }

        companion object {
            @JvmField
            @Suppress("unused")
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
