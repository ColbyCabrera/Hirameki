// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils.ext

import android.view.Menu
import androidx.appcompat.widget.Toolbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import kotlin.test.assertNotNull

fun AnkiActivity.menu(): Menu = assertNotNull(findViewById<Toolbar>(R.id.toolbar)?.menu)
