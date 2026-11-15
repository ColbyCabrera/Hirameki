// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import com.ichi2.anki.utils.Destination

class StatisticsDestination : Destination {
    override fun toIntent(context: Context): Intent = PageFragment.getIntent(context, "graphs", null, Statistics::class)
}
