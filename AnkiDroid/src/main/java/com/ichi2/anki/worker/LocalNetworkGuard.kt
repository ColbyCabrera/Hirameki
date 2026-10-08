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

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import com.ichi2.anki.Channel
import com.ichi2.anki.R
import com.ichi2.utils.Permissions
import timber.log.Timber

/**
 * Background work cannot prompt for ACCESS_LOCAL_NETWORK. Returns [ListenableWorker.Result.failure]
 * when a LAN custom-endpoint sync would block, so callers fail fast instead of hitting a
 * silent LAN timeout; null when the worker may proceed.
 */
fun CoroutineWorker.failFastIfLocalNetworkBlocked(
    workerTag: String,
    endpoint: String?,
    isCustomSyncEnabled: Boolean,
    notificationId: Int,
): ListenableWorker.Result? {
    if (!isCustomSyncEnabled || !Permissions.isLocalNetworkSyncBlocked(applicationContext, endpoint, true)) {
        return null
    }
    Timber.w("%s: LAN sync blocked without ACCESS_LOCAL_NETWORK", workerTag)
    if (Permissions.canPostNotifications(applicationContext)) {
        val text =
            applicationContext.getString(
                R.string.custom_sync_local_network_not_granted,
                Permissions.displayHost(endpoint),
            )
        val notification =
            NotificationCompat
                .Builder(applicationContext, Channel.SYNC.id)
                .apply {
                    priority = NotificationCompat.PRIORITY_LOW
                    setSmallIcon(R.drawable.ic_star_notify)
                    setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    setSilent(true)
                    setContentTitle(applicationContext.getString(R.string.sync_error))
                    setContentText(text)
                }.build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }
    return ListenableWorker.Result.failure()
}
