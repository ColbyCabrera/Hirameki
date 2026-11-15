// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.common.utils.android

import android.os.Build

val isRobolectric get() = Build.FINGERPRINT?.startsWith("robolectric") ?: false
