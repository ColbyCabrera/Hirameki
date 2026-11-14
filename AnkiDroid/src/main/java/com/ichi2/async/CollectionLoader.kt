// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.async

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object CollectionLoader {
    fun interface Callback {
        fun execute(col: Collection?)
    }

    fun load(
        lifecycleOwner: LifecycleOwner,
        callback: Callback,
    ) {
        lifecycleOwner.lifecycleScope.launch {
            val col =
                withContext(ioDispatcher) {
                    // load collection
                    try {
                        Timber.d("CollectionLoader accessing collection")
                        val col = CollectionManager.getColUnsafe()
                        Timber.i("CollectionLoader obtained collection")
                        col
                    } catch (e: RuntimeException) {
                        Timber.e(e, "loadInBackground - RuntimeException on opening collection")
                        CrashReportService.sendExceptionReport(e, "CollectionLoader.load")
                        null
                    }
                }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                callback.execute(col)
            }
        }
    }
}
