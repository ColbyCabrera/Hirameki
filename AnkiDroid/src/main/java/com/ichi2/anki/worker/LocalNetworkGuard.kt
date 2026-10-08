/*
 Copyright (c) 2026 Colby Cabrera <colby.cabrera@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
    if (!isCustomSyncEnabled || !isLocalNetworkSyncBlocked(applicationContext, endpoint, true)) {
        return null
    }
    Timber.w("%s: LAN sync blocked without ACCESS_LOCAL_NETWORK; skipping", workerTag)
    return ListenableWorker.Result.failure()
}
