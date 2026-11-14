// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import com.ichi2.anki.cardviewer.GestureProcessor
import com.ichi2.anki.dialogs.CardSideSelectionDialog
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding

class ReviewerControlPreference : ControlPreference {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    /** The action associated to this preference */
    private val viewerAction get() = ViewerAction.fromPreferenceKey(key)

    override val areGesturesEnabled: Boolean
        get() = sharedPreferences?.let { GestureProcessor.isEnabledIn(it) } ?: GestureProcessor.DEFAULT_ENABLED

    override fun getMappableBindings(): List<ReviewerBinding> = ReviewerBinding.fromPreferenceString(value).toList()

    override fun onKeySelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    override fun onGestureSelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    override fun onAxisSelected(binding: Binding) {
        selectSide { side ->
            addBinding(binding, side)
        }
    }

    private fun addBinding(
        binding: Binding,
        side: CardSide,
    ) {
        val newBinding = ReviewerBinding(binding, side)
        getPreferenceAssignedTo(binding)?.removeMappableBinding(newBinding)
        val bindings = ReviewerBinding.fromPreferenceString(value).toMutableList()
        bindings.add(newBinding)
        value = bindings.toPreferenceString()
    }

    /**
     * If this command can be executed on a single side, execute the callback on this side.
     * Otherwise, ask the user to select one or two side(s) and execute the callback on them.
     */
    private fun selectSide(callback: (c: CardSide) -> Unit) {
        if (viewerAction == ViewerAction.SHOW_ANSWER) {
            callback(CardSide.QUESTION)
        } else {
            CardSideSelectionDialog.displayInstance(context, callback)
        }
    }
}
