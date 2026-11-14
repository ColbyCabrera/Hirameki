// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.settings.enums

import androidx.annotation.StringRes
import com.ichi2.anki.R

/** [R.array.sync_media_values] */
enum class ShouldFetchMedia(
    @StringRes
    override val entryResId: Int,
) : PrefEnum {
    ALWAYS(R.string.sync_media_always_value),
    ONLY_UNMETERED(R.string.sync_media_only_unmetered_value),
    NEVER(R.string.sync_media_never_value),
}
