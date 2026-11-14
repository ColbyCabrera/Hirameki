// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.analytics

import androidx.fragment.app.DialogFragment

abstract class AnalyticsDialogFragment : DialogFragment() {
    override fun onResume() {
        super.onResume()
        UsageAnalytics.sendAnalyticsScreenView(this)
    }
}
