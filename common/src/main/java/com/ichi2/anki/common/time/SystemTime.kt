// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.common.time

import com.ichi2.anki.common.time.Time

class SystemTime : Time() {
    override fun intTimeMS(): Long = System.currentTimeMillis()
}
