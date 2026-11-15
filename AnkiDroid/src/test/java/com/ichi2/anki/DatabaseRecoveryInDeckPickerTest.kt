// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialog.ShowDatabaseErrorDialog
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.showDatabaseErrorDialogInDeckPicker
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The database recovery steps are run by the [DeckPicker]: other screens hand them to it rather
 * than crashing on `activity as DeckPicker` (ported from ankidroid/Anki-Android#22941).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryInDeckPickerTest : RobolectricTest() {
    /** Controls the Compose clock: otherwise the screens' endless animations keep the looper busy */
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun allowTheDeckPickerToOpen() {
        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
    }

    @After
    fun discardStoredMessage() = DialogHandler.discardMessage()

    @Test
    fun `an error on another screen opens the Deck Picker with the dialog and its stack trace`() {
        val otherScreen = startRegularActivity<DrawingActivity>()

        otherScreen.showDatabaseErrorDialogInDeckPicker(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, CustomExceptionData("stack"))

        assertNull(otherScreen.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG), "not shown on the other screen")
        val deckPicker = otherScreen.openedDeckPicker()
        val dialog = deckPicker.awaitDatabaseErrorDialog()
        assertEquals(targetContext.getString(R.string.open_collection_failed_title), dialog.title)
        assertEquals("stack", dialog.exceptionData()?.toHumanReadableString())
    }

    @Test
    fun `Options on a load failure shows the recovery options in the Deck Picker`() {
        val otherScreen = startRegularActivity<DrawingActivity>()
        otherScreen.showDatabaseErrorDialogInDeckPicker(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
        val deckPicker = otherScreen.openedDeckPicker()

        (deckPicker.awaitDatabaseErrorDialog().requireDialog() as AlertDialog).performPositiveClick()
        advanceRobolectricLooper()

        val recoveryOptions = deckPicker.awaitDatabaseErrorDialog()
        assertEquals(targetContext.getString(R.string.error_handling_title), recoveryOptions.title)
    }

    @Test
    fun `a stored message replayed on another screen opens the Deck Picker`() {
        val otherScreen = startRegularActivity<DrawingActivity>()

        ShowDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK).handleAsyncMessage(otherScreen)

        assertNull(otherScreen.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG), "not shown on the other screen")
        val dialog = otherScreen.openedDeckPicker().awaitDatabaseErrorDialog()
        assertEquals(targetContext.getString(R.string.check_db_title), dialog.title)
    }

    @Test
    fun `a corrupt collection error on another screen opens the Deck Picker`() {
        val otherScreen = startRegularActivity<DrawingActivity>()

        // before the fix, this showed a generic error without the recovery options
        otherScreen.launchCatchingTask { throw SQLiteDatabaseCorruptException("corrupt") }
        advanceRobolectricLooper()

        val dialog = otherScreen.openedDeckPicker().awaitDatabaseErrorDialog()
        assertEquals(targetContext.getString(R.string.open_collection_failed_title), dialog.title)
        assertTrue(dialog.exceptionData()!!.toHumanReadableString().contains("corrupt"))
    }

    @Test
    fun `the Deck Picker shows the dialog directly`() {
        val deckPicker = startRegularActivity<DeckPicker>()
        shadowOf(deckPicker).clearNextStartedActivities()

        deckPicker.showDatabaseErrorDialogInDeckPicker(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)

        assertNull(shadowOf(deckPicker).nextStartedActivity, "no activity should be started")
        assertEquals(targetContext.getString(R.string.open_collection_failed_title), deckPicker.awaitDatabaseErrorDialog().title)
    }

    private fun AnkiActivity.openedDeckPicker(): DeckPicker {
        val intent = assertNotNull(shadowOf(this).nextStartedActivity, "the Deck Picker was not opened")
        assertEquals(DeckPicker::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
    }

    private fun AnkiActivity.awaitDatabaseErrorDialog(): DatabaseErrorDialog {
        supportFragmentManager.executePendingTransactions()
        advanceRobolectricLooper()
        val fragment = supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
        return assertNotNull(fragment as? DatabaseErrorDialog, "expected a DatabaseErrorDialog, got $fragment")
    }

    private val DatabaseErrorDialog.title: String get() = (requireDialog() as AlertDialog).title

    private fun DatabaseErrorDialog.exceptionData(): CustomExceptionData? =
        androidx.core.os.BundleCompat
            .getParcelable(requireArguments(), "exception", CustomExceptionData::class.java)
}
