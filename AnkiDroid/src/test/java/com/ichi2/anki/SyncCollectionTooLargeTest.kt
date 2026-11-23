// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Soumya Ranjan Nanda <srnranjan2005@gmail.com>

package com.ichi2.anki

import android.content.DialogInterface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.sync.SyncStatusResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class SyncCollectionTooLargeTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setUpTooLargeTest() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
        Prefs.hkey = "test-hkey"
    }

    @After
    fun tearDownTooLargeTest() {
        unmockkObject(CollectionManager)
    }

    @Test
    fun `isCollectionTooLargeException matches localized and raw byte messages`() {
        assertTrue(
            BackendSyncException(
                backendError { message = TR.syncUploadTooLarge("300.14 MB > 300.00 MB") },
            ).isCollectionTooLargeException(),
        )
        assertTrue(
            BackendSyncException(
                backendError { message = "Some localized backend message: 123456789 > 314572800" },
            ).isCollectionTooLargeException(),
        )
        assertFalse(
            BackendSyncException(
                backendError { message = "unexpected sync error" },
            ).isCollectionTooLargeException(),
        )
    }

    @Test
    fun `collection too large uses database check dialog`() {
        val message = TR.syncUploadTooLarge("300.14 MB > 300.00 MB")
        val deckPicker = startRegularActivity<DeckPicker>()
        val spyCol = spyk(col)
        val mockBackend =
            mockk<Backend>(relaxed = true) {
                every { syncStatus(any()) } returns SyncStatusResponse.getDefaultInstance()
                every { tr } returns TR
            }
        every { spyCol.syncCollection(any(), any()) } throws
            BackendSyncException(backendError { this.message = message })
        CollectionManager.setColForTests(spyCol)
        mockkObject(CollectionManager)
        every { CollectionManager.getBackend() } returns mockBackend

        deckPicker.handleNewSync(conflict = null, syncMedia = false)
        waitForCondition("SyncErrorDialog to be shown") {
            deckPicker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is SyncErrorDialog
        }

        val dialogFragment =
            assertIs<SyncErrorDialog>(deckPicker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
        val dialog = assertIs<AlertDialog>(dialogFragment.dialog)
        assertEquals(
            message,
            dialog.findViewById<TextView>(android.R.id.message)?.text?.toString(),
        )
        assertEquals(
            targetContext.getString(R.string.check_db),
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString(),
        )
        assertEquals(
            targetContext.getString(R.string.dialog_cancel),
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).text.toString(),
        )

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        waitForCondition("Database check rebuilt dialog to be shown") {
            (ShadowDialog.getLatestDialog() as? AlertDialog)
                ?.findViewById<TextView>(android.R.id.message)
                ?.text
                ?.toString() == TR.databaseCheckRebuilt()
        }

        assertFalse(dialog.isShowing)
        val rebuiltMessage =
            (ShadowDialog.getLatestDialog() as AlertDialog)
                .findViewById<TextView>(android.R.id.message)
                ?.text
                ?.toString()
        assertEquals(TR.databaseCheckRebuilt(), rebuiltMessage)
    }

    private fun waitForCondition(
        description: String,
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        val deadline = TimeSource.Monotonic.markNow() + timeoutMs.milliseconds
        while (deadline.hasNotPassedNow()) {
            advanceRobolectricLooper()
            if (condition()) return
            Thread.sleep(20)
        }
        advanceRobolectricLooper()
        if (!condition()) {
            fail("Timed out waiting for: $description")
        }
    }
}
