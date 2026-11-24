// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Nishant <nishantbhandari0019@gmail.com>

package com.ichi2.anki

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.MeteredSyncDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.NetworkUtils
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class DeckPickerMeteredConfirmationTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var controller: ActivityController<DeckPicker>

    private var metered = true

    @Before
    fun setUpMeteredSyncTest() {
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
            putBoolean(getResourceString(R.string.custom_sync_server_enabled_key), false)
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(ShouldFetchMedia.ALWAYS.entryResId))
        }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }

        Prefs.hkey = "test-hkey"
        Prefs.allowSyncOnMeteredConnections = false

        mockkObject(NetworkUtils)
        every { NetworkUtils.isActiveNetworkMetered() } answers { metered }
        every { NetworkUtils.isOnline } returns true

        mockkStatic("com.ichi2.anki.SyncKt")
        every { any<DeckPicker>().handleNewSync(any(), any()) } just Runs

        controller = Robolectric.buildActivity(DeckPicker::class.java, Intent()).setup()
        saveControllerForCleanup(controller)
    }

    @After
    fun tearDownMeteredSyncTest() {
        unmockkStatic("com.ichi2.anki.SyncKt")
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `recreation retains metered sync confirmation, media choice, and checkbox state`() {
        val deckPicker = controller.get()
        deckPicker.sync()
        advanceRobolectricLooper()

        val initialDialog = assertIs<MeteredSyncDialog>(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        assertNotNull(initialDialog.dialog)

        val checkBoxMatcher = isToggleable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        composeTestRule
            .onNode(checkBoxMatcher)
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeTestRule.waitForIdle()
        assertTrue(Prefs.allowSyncOnMeteredConnections)

        // Change sync-media pref before recreation; the retained dialog keeps the original media choice
        editPreferences {
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(ShouldFetchMedia.NEVER.entryResId))
        }
        controller.recreate()
        advanceRobolectricLooper()

        val recreated = controller.get()
        val recreatedDialog =
            assertIs<MeteredSyncDialog>(recreated.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        val alertDialog = assertIs<AlertDialog>(recreatedDialog.dialog)
        composeTestRule.onNode(isToggleable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOn()

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        verify(exactly = 1) {
            recreated.handleNewSync(null, true)
        }
        assertNull(recreated.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
    }

    @Test
    fun `cancel dismisses metered dialog without syncing`() {
        val deckPicker = controller.get()
        deckPicker.sync()
        advanceRobolectricLooper()

        val dialogFragment = assertIs<MeteredSyncDialog>(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        val alertDialog = assertIs<AlertDialog>(dialogFragment.dialog)

        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()

        verify(exactly = 0) {
            deckPicker.handleNewSync(any(), any())
        }
        assertFalse(deckPicker.viewModel.isSyncing.value)
        assertEquals(null, deckPicker.viewModel.deferredMeteredSyncMedia)
        assertNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
    }

    @Test
    fun `deferred metered confirmation shows when resumed`() {
        val deckPicker = controller.get()
        controller.pause()

        deckPicker.sync()
        advanceRobolectricLooper()

        assertNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        assertEquals(true, deckPicker.viewModel.deferredMeteredSyncMedia)

        controller.resume()
        advanceRobolectricLooper()

        assertNotNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        assertNull(deckPicker.viewModel.deferredMeteredSyncMedia)
    }

    @Test
    fun `sync from login on a metered network waits for confirmation`() {
        val loginController =
            Robolectric.buildActivity(DeckPicker::class.java, Intent().putExtra(DeckPicker.INTENT_SYNC_FROM_LOGIN, true)).setup()
        saveControllerForCleanup(loginController)
        advanceRobolectricLooper()

        val deckPicker = loginController.get()
        assertNotNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        verify(exactly = 0) { deckPicker.handleNewSync(any(), any()) }
    }

    @Test
    fun `unmetered sync does not show a warning`() {
        metered = false
        val deckPicker = controller.get()

        deckPicker.sync()
        advanceRobolectricLooper()

        assertNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        verify(exactly = 1) { deckPicker.handleNewSync(null, true) }
    }

    @Test
    fun `allowed metered sync does not show a warning`() {
        Prefs.allowSyncOnMeteredConnections = true
        val deckPicker = controller.get()

        deckPicker.sync()
        advanceRobolectricLooper()

        assertNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        verify(exactly = 1) { deckPicker.handleNewSync(null, true) }
    }

    @Test
    fun `conflict retries skip the warning`() {
        val deckPicker = controller.get()

        for (conflict in ConflictResolution.entries) {
            deckPicker.viewModel.isSyncing.value = false
            deckPicker.sync(conflict)
            advanceRobolectricLooper()
            verify(exactly = 1) { deckPicker.handleNewSync(conflict, true) }
        }

        assertNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
    }

    @Test
    fun `recreation without a pending decision does not sync`() {
        controller.recreate()
        advanceRobolectricLooper()

        val recreated = controller.get()
        assertNull(recreated.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        verify(exactly = 0) { recreated.handleNewSync(any(), any()) }
    }

    @Test
    fun `pending confirmation blocks repeated requests after changing to an unmetered network`() {
        val deckPicker = controller.get()
        deckPicker.sync()
        advanceRobolectricLooper()
        val dialog = assertIs<MeteredSyncDialog>(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        metered = false
        editPreferences {
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(ShouldFetchMedia.NEVER.entryResId))
        }

        deckPicker.sync()
        advanceRobolectricLooper()

        assertEquals(dialog, deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))
        verify(exactly = 0) { deckPicker.handleNewSync(any(), any()) }
        assertIs<AlertDialog>(dialog.dialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        verify(exactly = 1) { deckPicker.handleNewSync(null, true) }
    }

    @Test
    fun `confirmation opened during the automatic status query blocks media only completion`() {
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            putBoolean(getResourceString(R.string.sync_status_badge_key), false)
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(ShouldFetchMedia.NEVER.entryResId))
        }
        Prefs.lastSyncTime = 0
        metered = false
        val backend = CollectionManager.getBackend()
        mockkObject(backend)
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        every { backend.syncStatus(any<SyncAuth>()) } answers {
            queryEntered.countDown()
            check(releaseQuery.await(30, TimeUnit.SECONDS))
            SyncStatusResponse.newBuilder().setRequired(SyncStatusResponse.Required.NO_CHANGES).build()
        }
        val activityTasks = mutableListOf<Job>()
        mockkStatic("com.ichi2.anki.CoroutineHelpersKt")
        every { any<DeckPicker>().launchCatchingTask(any(), any(), any()) } answers {
            callOriginal().also(activityTasks::add)
        }
        try {
            val autoSyncController = Robolectric.buildActivity(DeckPicker::class.java, Intent()).setup()
            saveControllerForCleanup(autoSyncController)
            advanceRobolectricLooperUntil { queryEntered.count == 0L }
            metered = true
            val deckPicker = autoSyncController.get()
            deckPicker.sync()
            advanceRobolectricLooper()
            assertNotNull(deckPicker.supportFragmentManager.findFragmentByTag(MeteredSyncDialog.TAG))

            releaseQuery.countDown()
            advanceRobolectricLooperUntil { activityTasks.all { it.isCompleted } }

            verify(exactly = 0) { deckPicker.handleNewSync(any(), any()) }
            assertEquals(0L, Prefs.lastSyncTime, "Pending confirmation must stop automatic sync before recording completion")
        } finally {
            releaseQuery.countDown()
            unmockkStatic("com.ichi2.anki.CoroutineHelpersKt")
            unmockkObject(backend)
        }
    }

    private fun advanceRobolectricLooperUntil(condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 30.seconds
        while (!condition()) {
            check(deadline.hasNotPassedNow()) { "Timed out waiting for condition" }
            advanceRobolectricLooper()
            Thread.yield()
        }
    }
}
