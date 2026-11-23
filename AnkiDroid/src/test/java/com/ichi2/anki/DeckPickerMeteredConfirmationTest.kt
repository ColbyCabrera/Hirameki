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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerMeteredConfirmationTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var controller: ActivityController<DeckPicker>

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
        every { NetworkUtils.isActiveNetworkMetered() } returns true
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
}
