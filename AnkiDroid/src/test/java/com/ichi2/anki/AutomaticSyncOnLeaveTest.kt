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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.sync.SyncStatusResponse
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.worker.UniqueWorkNames
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** tests of [AutomaticSyncOnLeave] */
@RunWith(AndroidJUnit4::class)
class AutomaticSyncOnLeaveTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        Prefs.putBoolean(R.string.automatic_sync_choice_key, true)
        Prefs.hkey = "hkey"
        // the metered check has its own preference; these tests are about the other conditions
        Prefs.allowSyncOnMeteredConnections = true
        setOnline()
        // a collection that never synced needs a one-way sync, which a sync on leave does not start.
        // marking it synced at its last schema change, then changing it, leaves a normal sync to do.
        // the status check reports both without asking a server
        col.db.execute("update col set scm = scm - 1000, ls = scm - 1000, mod = scm - 1000")
        addBasicNote()
        assertEquals(SyncStatusResponse.Required.NORMAL_SYNC, requiredSync())
    }

    @Test
    fun `leaving from the last screen starts a background sync`() =
        runTest {
            val hook = AutomaticSyncOnLeave(targetContext, this)
            val deckList = mock<Activity>()
            val reviewer = mock<Activity>()

            hook.onActivityStarted(deckList)
            hook.onActivityStarted(reviewer)
            hook.onActivityStopped(deckList)
            assertNull(hook.pendingCheck, "moving between screens is not leaving the app")

            hook.onActivityStopped(reviewer)
            assertNotNull(hook.pendingCheck).join()

            assertThat(syncWorkStates(), contains(WorkInfo.State.ENQUEUED))
        }

    @Test
    fun `a configuration change is not leaving the app`() {
        val hook = AutomaticSyncOnLeave(targetContext, pausedScope())
        val rotating = mock<Activity> { on { isChangingConfigurations } doReturn true }

        hook.onActivityStarted(rotating)
        hook.onActivityStopped(rotating)

        assertNull(hook.pendingCheck)
    }

    @Test
    fun `coming back cancels checks that have not finished`() {
        val hook = AutomaticSyncOnLeave(targetContext, pausedScope())
        val activity = mock<Activity>()

        hook.onActivityStarted(activity)
        hook.onActivityStopped(activity)
        val check = assertNotNull(hook.pendingCheck)
        hook.onActivityStarted(activity)

        assertTrue(check.isCancelled)
    }

    @Test
    fun `coming back cancels a sync that has not started`() =
        runTest {
            val hook = AutomaticSyncOnLeave(targetContext, this)
            val activity = mock<Activity>()

            hook.onActivityStarted(activity)
            hook.onActivityStopped(activity)
            assertNotNull(hook.pendingCheck).join()
            assertThat(syncWorkStates(), contains(WorkInfo.State.ENQUEUED))

            hook.onActivityStarted(activity)
            assertNotNull(hook.latestWork).join()

            assertThat(syncWorkStates(), contains(WorkInfo.State.CANCELLED))
        }

    @Test
    fun `no sync when automatic sync is disabled`() =
        runTest {
            Prefs.putBoolean(R.string.automatic_sync_choice_key, false)

            assertNull(AutomaticSyncOnLeave(targetContext, this).syncOnLeave())
            assertThat(syncWorkStates(), empty())
        }

    @Test
    fun `no sync when logged out`() =
        runTest {
            Prefs.hkey = ""

            assertNull(AutomaticSyncOnLeave(targetContext, this).syncOnLeave())
            assertThat(syncWorkStates(), empty())
        }

    @Test
    fun `no sync while a sync is running`() =
        runTest {
            val hook = AutomaticSyncOnLeave(targetContext, this)

            val delay = trackRunningSync { hook.syncOnLeave() }

            assertNull(delay)
            assertThat(syncWorkStates(), empty())
        }

    @Test
    fun `a one-way sync is left to the sync the user sees`() =
        runTest {
            col.db.execute("update col set ls = 0")
            assertEquals(SyncStatusResponse.Required.FULL_SYNC, requiredSync())

            assertNull(AutomaticSyncOnLeave(targetContext, this).syncOnLeave())
            assertThat(syncWorkStates(), empty())
        }

    @Test
    fun `syncs on leave start at least an interval apart`() =
        runTest {
            useExactTime()
            val hook = AutomaticSyncOnLeave(targetContext, this)

            assertEquals(Duration.ZERO, hook.syncOnLeave(), "first leave")

            collectionTime.addM(1)
            assertEquals(4.minutes, hook.syncOnLeave(), "a leave inside the interval defers its sync to the interval's end")

            collectionTime.addM(1)
            assertEquals(3.minutes, hook.syncOnLeave(), "a later leave starts together with the deferred sync")

            collectionTime.addM(4)
            assertEquals(4.minutes, hook.syncOnLeave(), "the next sync waits an interval after the deferred one")

            collectionTime.addM(10)
            assertEquals(Duration.ZERO, hook.syncOnLeave(), "leaving once the interval has passed")

            collectionTime.addH(-1)
            assertEquals(Duration.ZERO, hook.syncOnLeave(), "a clock moved back does not hold syncs back")
        }

    @Test
    fun `a leave inside the interval queues a delayed sync`() =
        runTest {
            useExactTime()
            val hook = AutomaticSyncOnLeave(targetContext, this)
            val activity = mock<Activity>()

            hook.onActivityStarted(activity)
            hook.onActivityStopped(activity)
            assertNotNull(hook.pendingCheck).join()
            // coming back cancels that sync before it starts
            hook.onActivityStarted(activity)
            assertNotNull(hook.latestWork).join()

            collectionTime.addM(1)
            hook.onActivityStopped(activity)
            assertNotNull(hook.pendingCheck).join()

            val queued = syncWorkInfos().single { it.state == WorkInfo.State.ENQUEUED }
            assertEquals(4.minutes.inWholeMilliseconds, queued.initialDelayMillis)
        }

    /** a scope whose coroutines never run, so a launched check stays pending */
    private fun pausedScope() = TestScope(StandardTestDispatcher())

    private fun syncWorkInfos(): List<WorkInfo> =
        WorkManager
            .getInstance(targetContext)
            .getWorkInfosForUniqueWork(UniqueWorkNames.SYNC)
            .get()

    private fun syncWorkStates() = syncWorkInfos().map { it.state }

    private fun requiredSync() = col.backend.syncStatus(assertNotNull(syncAuth())).required

    /** the default test clock moves on every read, which would blur the intervals these tests compare */
    private fun useExactTime() = TimeManager.resetWith(MockTime(TimeManager.time.intTimeMS()))

    private fun setOnline() {
        val connectivityManager = assertNotNull(targetContext.getSystemService<ConnectivityManager>())
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        }
        shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
    }
}
