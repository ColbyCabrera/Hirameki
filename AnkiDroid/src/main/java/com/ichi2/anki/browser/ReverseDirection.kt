// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.browser

import com.ichi2.anki.libanki.Config
import timber.log.Timber

/**
 * Whether searches should be reversed
 */
data class ReverseDirection(
    val isSortDescending: Boolean,
) {
    // TODO: This likely needs to handle 'CardsOrNotes'
    fun updateConfig(config: Config) {
        Timber.v("update config to %s", this)
        config.set("sortBackwards", isSortDescending)
        config.set("browserNoteSortBackwards", isSortDescending)
    }

    companion object {
        fun fromConfig(config: Config): ReverseDirection = ReverseDirection(isSortDescending = config.get("sortBackwards") ?: false)
    }
}
