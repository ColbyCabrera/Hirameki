/*
 * Copyright (c) 2026 Hirameki contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import anki.sync.SyncStatusResponse
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.ui.dialogs.DefaultActivityLifecycleCallbacks
import com.ichi2.anki.worker.SyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * runs the automatic sync when the user leaves the app: backing out of the last screen, going home,
 * switching to another app or turning the screen off.
 *
 * upstream ankidroid synced from a back callback on the deck list. that callback stopped the
 * system's predictive back animation and missed every other way out, and this fork removed it.
 * watching for the last started activity to stop sees every way out and never intercepts back.
 *
 * the checks are quick and run off the main thread where they touch the collection; the sync
 * itself is handed to [SyncWorker], which WorkManager runs even if this process is cached or
 * killed right after the user left. a sync that has not started when the user comes back is
 * cancelled: it would hold the collection, and so the screens, while the user is in the app.
 */
class AutomaticSyncOnLeave(
    private val context: Context,
    private val scope: CoroutineScope,
) : DefaultActivityLifecycleCallbacks {
    private var startedActivities = 0

    /**
     * when the latest sync on leave started, from [TimeManager]. for a deferred sync, when it is due to
     * start, so later leaves wait for it instead of adding another. null until the first one
     */
    private var lastSyncStart: Long? = null

    /** the checks started by the latest leave, cancelled if the user comes back before they finish */
    @VisibleForTesting
    var pendingCheck: Job? = null
        private set

    /**
     * the work started by the latest leave or return. each waits for the one before, so a return never
     * checks for a queued sync before the leave ahead of it queued one, nor cancels one a later leave queued
     */
    @VisibleForTesting
    var latestWork: Job? = null
        private set

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities++ > 0) return
        // the user is back. a sync started now would hold the collection while they use the app
        pendingCheck?.cancel()
        launchInOrder { SyncWorker.cancelIfNotStarted(context) }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        // moving between screens starts the next activity before the last one stops, and a
        // configuration change restarts the activity straight away: neither is leaving the app
        if (startedActivities > 0 || activity.isChangingConfigurations) return
        pendingCheck = launchInOrder { syncOnLeave() }
    }

    private fun launchInOrder(block: suspend () -> Unit): Job {
        val previous = latestWork
        return scope
            .launch {
                previous?.join()
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // no screen is left to report on, and the app scope would crash on it; the next sync
                    // the user sees (on app start or by hand) reports a lasting problem such as a lost login
                    Timber.w(e, "sync on leave failed")
                }
            }.also { latestWork = it }
    }

    /**
     * @return how long the collection sync waits before it starts: zero when it starts now,
     * null when no collection sync was queued
     */
    @VisibleForTesting
    suspend fun syncOnLeave(): Duration? {
        val status = automaticSyncStatus(checkInterval = false) ?: return null
        val now = TimeManager.time.intTimeMS()
        val delay = delayBeforeNextSync(now)
        when (status.required) {
            SyncStatusResponse.Required.NORMAL_SYNC -> {
                Timber.i("sync on leave: starting in %s", delay)
                SyncWorker.start(context, status.auth, shouldFetchMedia(), initialDelay = delay)
                lastSyncStart = now + delay.inWholeMilliseconds
                return delay
            }
            // it asks the user which way to go. the sync on app start does that
            SyncStatusResponse.Required.FULL_SYNC -> Timber.d("sync on leave: a one-way sync is required")
            SyncStatusResponse.Required.NO_CHANGES,
            SyncStatusResponse.Required.UNRECOGNIZED,
            -> {
                // media alone is not worth a deferred sync; the next sync takes it along
                if (delay.isPositive()) {
                    Timber.d("sync on leave: no collection changes, and the last sync is recent")
                } else {
                    syncMediaWithoutCollectionChanges(context, status.auth)
                    lastSyncStart = now
                }
            }
        }
        return null
    }

    /**
     * how long a sync started now waits so that syncs on leave start at least
     * [LEAVE_SYNC_MINIMAL_INTERVAL] apart. a leave inside the interval defers its sync rather than
     * dropping it: the reviews done since the last sync would otherwise wait for a later leave
     */
    private fun delayBeforeNextSync(now: Long): Duration {
        val lastStart = lastSyncStart ?: return Duration.ZERO
        val sinceLastStart = (now - lastStart).milliseconds
        return when {
            // a deferred sync is due later: start together with it
            sinceLastStart.isNegative() && -sinceLastStart <= LEAVE_SYNC_MINIMAL_INTERVAL -> -sinceLastStart
            // a clock moved back by more than the interval must not hold syncs back until it catches up
            sinceLastStart.isNegative() -> Duration.ZERO
            sinceLastStart < LEAVE_SYNC_MINIMAL_INTERVAL -> LEAVE_SYNC_MINIMAL_INTERVAL - sinceLastStart
            else -> Duration.ZERO
        }
    }

    companion object {
        /**
         * leaving and coming back often, e.g. to look a word up while reviewing, starts at most one
         * sync in this interval. the automatic sync preference summary (automatic_sync_choice_summ)
         * states this number
         */
        val LEAVE_SYNC_MINIMAL_INTERVAL = 5.minutes
    }
}
