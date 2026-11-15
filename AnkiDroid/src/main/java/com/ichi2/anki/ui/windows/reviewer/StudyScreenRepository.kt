// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.ui.windows.reviewer

import android.content.SharedPreferences
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ToolbarPosition
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class StudyScreenRepository(
    preferences: SharedPreferences,
) {
    val isMarkShownInToolbar: Boolean
    val isFlagShownInToolbar: Boolean

    init {
        val actions =
            ReviewerMenuRepository(preferences)
                .getActionsByMenuDisplayTypes(
                    MenuDisplayType.ALWAYS,
                ).getValue(MenuDisplayType.ALWAYS)
        val isToolbarShown = Prefs.toolbarPosition != ToolbarPosition.NONE
        isMarkShownInToolbar = isToolbarShown && ViewerAction.MARK in actions
        isFlagShownInToolbar = isToolbarShown && ViewerAction.FLAG_MENU in actions
    }

    companion object {
        fun getServerPort(): Int {
            if (!Prefs.useFixedPortInReviewer) return 0
            return try {
                ServerSocket(Prefs.reviewerPort)
                    .use {
                        it.reuseAddress = true
                        it.localPort
                    }.also {
                        if (Prefs.reviewerPort == 0) {
                            Prefs.reviewerPort = it
                        }
                    }
            } catch (_: BindException) {
                Timber.w("Fixed port %d under use. Using dynamic port", Prefs.reviewerPort)
                0
            }
        }
    }
}
