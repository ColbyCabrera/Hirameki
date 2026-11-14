// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.compat.setTooltipTextCompat
import com.ichi2.ui.RtlCompliantActionProvider.Companion.unwrapContext

/**
 * Hosts the sync button action view and exposes helpers (e.g., tooltip, progress visibility).
 *
 * Progress visibility and the badge are controlled by DeckPicker
 * (see [DeckPicker.updateSyncIconFromState] and setupMediaSyncMenuItem), not by this provider.
 *
 * The badge is shown when the collection is "dirty" (local changes not yet synced).
 */
class SyncActionProvider(
    context: Context,
) : ActionProviderCompat(context) {
    val activity: Activity = unwrapContext(context)

    private var progressIndicator: LinearProgressIndicator? = null
    private var syncButton: AppCompatImageButton? = null

    val isProgressShown: Boolean
        get() = progressIndicator?.isVisible == true

    var icon: Drawable?
        get() = syncButton?.drawable
        set(value) {
            syncButton?.setImageDrawable(value)
        }

    override fun onCreateActionView(forItem: MenuItem): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.sync_progress_layout, null)

        progressIndicator = view.findViewById(R.id.progress_indicator)
        syncButton =
            view.findViewById<AppCompatImageButton>(R.id.button).apply {
                setOnClickListener {
                    if (!forItem.isEnabled) {
                        return@setOnClickListener
                    }
                    activity.onOptionsItemSelected(forItem)
                }
            }

        return view
    }

    fun setTooltipText(value: CharSequence) {
        syncButton?.setTooltipTextCompat(value)
    }

    fun setContentDescription(value: CharSequence) {
        syncButton?.contentDescription = value
    }
}
