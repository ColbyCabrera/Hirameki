// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.worker

import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import com.ichi2.anki.isLocalNetworkSyncBlocked
import timber.log.Timber

/**
 * Background work cannot prompt for ACCESS_LOCAL_NETWORK. Returns [ListenableWorker.Result.failure]
 * when a LAN custom-endpoint sync would block, so callers skip it instead of hitting a silent LAN
 * timeout; null when the worker may proceed.
 *
 * The block is silent by design: background sync never notifies here, and the foreground sync flow
 * is what teaches the user to grant the permission.
 */
fun CoroutineWorker.failFastIfLocalNetworkBlocked(
    workerTag: String,
    endpoint: String?,
    isCustomSyncEnabled: Boolean,
): ListenableWorker.Result? {
    if (!isLocalNetworkSyncBlocked(applicationContext, endpoint, isCustomSyncEnabled)) {
        return null
    }
    Timber.w("%s: LAN sync blocked without ACCESS_LOCAL_NETWORK; skipping", workerTag)
    return ListenableWorker.Result.failure()
}
