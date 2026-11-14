// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>

package com.ichi2.testutils

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows

/** Executes [runnable] without [WRITE_EXTERNAL_STORAGE] and [READ_EXTERNAL_STORAGE] */
fun withNoWritePermission(runnable: (() -> Unit)) {
    try {
        revokeWritePermissions()
        runnable()
    } finally {
        grantWritePermissions()
    }
}

fun grantWritePermissions() {
    val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
    app.grantPermissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
}

fun revokeWritePermissions() {
    val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
    app.denyPermissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
}
