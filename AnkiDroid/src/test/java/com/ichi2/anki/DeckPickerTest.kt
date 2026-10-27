// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.INITIAL_FRAGMENT_EXTRA
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@KotlinCleanup("SPMockBuilder")
@RunWith(AndroidJUnit4::class)
class DeckPickerTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun before() {
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // Prevent BackupPromptDialog Compose overlay from blocking tests.
        // In Robolectric, getFirstInstallTime() returns 0 (epoch), making
        // the user appear non-new, so the dialog would otherwise show.
        targetContext
            .sharedPrefs()
            .edit { putBoolean(BackupPromptDialog.BACKUP_PROMPT_DISABLED, true) }
    }

    @Test
    fun getPreviousVersionUpgradeFrom201to292() {
        val newVersion = 20900302L // 2.9.2
        val preferences = getPreferences()
        preferences.edit { putString(DeckPicker.UPGRADE_VERSION_KEY, "2.0.1") }

        val deckPicker = Robolectric.buildActivity(DeckPicker::class.java, Intent()).get()
        val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
        assertEquals(0, previousVersion)
        assertTrue(!preferences.contains(DeckPicker.UPGRADE_VERSION_KEY))
    }

    @Test
    fun getPreviousVersionUpgradeFrom202to292() {
        val newVersion = 20900302L // 2.9.2
        val preferences = getPreferences()
        preferences.edit { putString(DeckPicker.UPGRADE_VERSION_KEY, "2.0.2") }

        val deckPicker = Robolectric.buildActivity(DeckPicker::class.java, Intent()).get()
        val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
        assertEquals(40, previousVersion)
        assertTrue(!preferences.contains(DeckPicker.UPGRADE_VERSION_KEY))
    }

    @Test
    fun getPreviousVersionUpgradeFrom281to291() {
        val prevVersion = 20800301 // 2.8.1
        val newVersion = 20900301L // 2.9.1
        val preferences = getPreferences()
        preferences.edit { putInt(DeckPicker.UPGRADE_VERSION_KEY, prevVersion) }

        val deckPicker = Robolectric.buildActivity(DeckPicker::class.java, Intent()).get()
        val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
        assertEquals(prevVersion.toLong(), previousVersion)
        assertTrue(!preferences.contains(DeckPicker.UPGRADE_VERSION_KEY))
    }

    @Test
    fun getPreviousVersionUpgradeFrom291to292() {
        val prevVersion = 20900301L // 2.9.1
        val newVersion = 20900302L // 2.9.2
        val preferences = getPreferences()
        preferences.edit { putLong(DeckPicker.UPGRADE_VERSION_KEY, prevVersion) }

        val deckPicker = Robolectric.buildActivity(DeckPicker::class.java, Intent()).get()
        val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
        assertEquals(prevVersion, previousVersion)
        assertTrue(preferences.contains(DeckPicker.UPGRADE_VERSION_KEY))
        preferences.edit { remove(DeckPicker.UPGRADE_VERSION_KEY) }
    }

    @Test
    fun limitAppliedAfterReview() {
        val sched = col.sched
        val dconf = col.decks.getConfig(1)
        assertNotNull(dconf)
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..10) {
            addBasicNote("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            scenario.onActivity { deckPicker ->
                assertEquals(
                    10,
                    deckPicker.viewModel.dueTree!!
                        .children[0]
                        .newCount
                        .toLong(),
                )
            }
        }
    }

    @Test
    fun deckPickerSkipsAnalyticsOptInDialogWhenAnalyticsDisabled() {
        try {
            grantWritePermissions()
            targetContext.sharedPrefs().edit {
                putString("lastVersion", "0.1")
                remove(UsageAnalytics.ANALYTICS_OPTIN_KEY)
            }

            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                composeTestRule.waitForIdle()
                scenario.onActivity { activity ->
                    assertFalse(
                        "Analytics opt-in should not be displayed",
                        activity.viewModel.showAnalyticsOptInDialog.value,
                    )
                }
            }
        } finally {
            targetContext.sharedPrefs().edit {
                remove("lastVersion")
                remove(UsageAnalytics.ANALYTICS_OPTIN_KEY)
            }
            revokeWritePermissions()
        }
    }

    // TODO: enableNullCollection() causes CollectionLockedException when DeckPicker startup
    //  coroutines try to open the collection. The exception propagates before the options
    //  menu can be inspected. Fix: use a mechanism that delays the lock until after startup.
    @Ignore("CollectionLockedException thrown during startup when null collection is enabled")
    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() =
        runTest {
            try {
                enableNullCollection()
                ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                    composeTestRule.waitForIdle()
                    advanceUntilIdle()
                    ShadowLooper.idleMainLooper()
                    var optionsMenuState: DeckPicker.OptionsMenuState? = null
                    scenario.onActivity { optionsMenuState = it.optionsMenuState }
                    assertNull(optionsMenuState)
                }
            } finally {
                disableNullCollection()
            }
        }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() =
        runTest {
            try {
                grantWritePermissions()
                ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                    composeTestRule.waitForIdle()
                    advanceUntilIdle()
                    ShadowLooper.idleMainLooper()
                    var deckPicker: DeckPicker? = null
                    scenario.onActivity { deckPicker = it }
                    assertNotNull(deckPicker)
                    deckPicker.updateMenuState()

                    scenario.onActivity { activity ->
                        val menu = MenuBuilder(activity)
                        activity.menuInflater.inflate(R.menu.deck_picker, menu)
                        menu.findItem(R.id.action_sync).actionView
                        activity.updateMenuFromState(menu)

                        val expectedSyncLabel =
                            when (activity.optionsMenuState?.syncIcon) {
                                SyncIconState.OneWay -> targetContext.getString(R.string.sync_menu_title_one_way_sync)
                                SyncIconState.NotLoggedIn -> targetContext.getString(R.string.sync_menu_title_no_account)
                                SyncIconState.PendingChanges,
                                SyncIconState.Normal,
                                null,
                                -> targetContext.getString(R.string.button_sync)
                            }

                        val syncButton =
                            menu.findItem(R.id.action_sync).actionView?.findViewById<AppCompatImageButton>(
                                R.id.button,
                            )

                        assertNotNull(syncButton)
                        assertEquals(expectedSyncLabel, syncButton.contentDescription)
                    }
                }
            } finally {
                revokeWritePermissions()
            }
        }

    @Test
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        try {
            revokeWritePermissions()
            enableNullCollection()
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                composeTestRule.waitForIdle()
                scenario.onActivity { d ->
                    assertFailsWith<CollectionLockedException> { d.getColUnsafe }
                }
            }
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() {
        try {
            grantWritePermissions()
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                composeTestRule.waitForIdle()
                scenario.onActivity { d ->
                    assertNotNull(d.getColUnsafe)
                    assertNotNull(d.getColUnsafe.notetypes)
                }
            }
        } finally {
            revokeWritePermissions()
        }
    }

    // TODO: ShadowDialog.getLatestDialog() returns null when cast to AlertDialog because dialogs
    //  are now shown via Compose or FragmentManager, not the legacy AlertDialog.Builder path.
    //  Fix: assert on FragmentManager dialog fragments or Compose dialog state instead.

    @Test
    fun `ContextMenu starts deck options for normal deck`() =
        deckPicker {
            val didA = addDeck("Deck 1")
            viewModel.openDeckOptions(didA).join()
            composeTestRule.waitForIdle()
            ShadowLooper.idleMainLooper()
            val deckOptionsNormal = Shadows.shadowOf(this).nextStartedActivity
            assertNotNull(deckOptionsNormal)
            assertEquals(
                "com.ichi2.anki.SingleFragmentActivity",
                deckOptionsNormal.component!!.className,
            )
        }

    @Test
    fun `ContextMenu starts deck options for dynamic deck`() =
        deckPicker {
            val didDynamicA = addDynamicDeck("Deck Dynamic 1")
            viewModel.openDeckOptions(didDynamicA).join()
            composeTestRule.waitForIdle()
            ShadowLooper.idleMainLooper()
            val deckOptionsDynamic = Shadows.shadowOf(this).nextStartedActivity
            assertNotNull(deckOptionsDynamic)
            assertEquals("com.ichi2.anki.FilteredDeckOptions", deckOptionsDynamic.component!!.className)
        }

    @Test
    fun `More menu 'Empty Cards' starts EmptyCardsDialogFragment`() =
        deckPicker {
            val menu = MenuBuilder(this)
            menuInflater.inflate(R.menu.deck_picker, menu)

            assertTrue(onOptionsItemSelected(menu.findItem(R.id.action_empty_cards)))
            supportFragmentManager.executePendingTransactions()

            val dialogFragment =
                supportFragmentManager.findFragmentByTag(EmptyCardsDialogFragment.TAG) as? EmptyCardsDialogFragment
            assertNotNull(dialogFragment, "EmptyCardsDialogFragment should be displayed")
            dismissAllDialogFragments()
        }

    /**
     * The 'Database Locked' dialog offers Settings, opened at the Advanced screen, where
     * changing the 'Hirameki directory' resolves the conflict. Without it, the dialog's only
     * button quits the app, contradicting the guidance in its own message.
     */
    @Test
    fun `database locked dialog links to Advanced settings`() {
        val deckPicker = startRegularActivity<DeckPicker>()
        deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
        deckPicker.supportFragmentManager.executePendingTransactions()
        advanceRobolectricLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val settingsButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
        assertEquals(targetContext.getString(R.string.settings), settingsButton.text.toString())
        assertEquals(
            targetContext.getString(R.string.database_locked_summary_new, targetContext.getString(R.string.col_path)),
            getAlertDialogText(true),
        )

        Shadows.shadowOf(deckPicker).clearNextStartedActivities()
        settingsButton.performClick()
        advanceRobolectricLooper()

        val settings = Shadows.shadowOf(deckPicker).nextStartedActivity
        assertNotNull(settings, "Advanced settings should be opened")
        assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
        assertEquals(
            AdvancedSettingsFragment::class.jvmName,
            settings.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)?.getString(INITIAL_FRAGMENT_EXTRA),
        )
    }

    /**
     * A [CollectionLockedException] shown via [showError] offers Settings, opened at the
     * Advanced screen, where changing the 'Hirameki directory' resolves the conflict.
     */
    @Test
    fun `locked collection error links to Advanced settings`() {
        throwOnShowError = false
        try {
            val deckPicker = startRegularActivity<DeckPicker>()
            val locked =
                CollectionLockedException(
                    BackendDbLockedException(backendError {}),
                )

            deckPicker.showError(
                locked.localizedMessage!!,
                CrashReportData(locked, "DeckPickerTest", reportableException = false),
            )
            advanceRobolectricLooper()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val settingsButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            assertEquals(targetContext.getString(R.string.settings), settingsButton.text.toString())

            Shadows.shadowOf(deckPicker).clearNextStartedActivities()
            settingsButton.performClick()
            advanceRobolectricLooper()

            val settings = Shadows.shadowOf(deckPicker).nextStartedActivity
            assertNotNull(settings, "Advanced settings should be opened")
            assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
            assertEquals(
                AdvancedSettingsFragment::class.jvmName,
                settings.getBundleExtra(SingleFragmentActivity.FRAGMENT_ARGS_EXTRA)?.getString(INITIAL_FRAGMENT_EXTRA),
            )
            assertTrue(
                Shadows.shadowOf(dialog).hasBeenDismissed(),
                "the error dialog should be dismissed after opening settings",
            )
        } finally {
            throwOnShowError = true
        }
    }

    private fun deckPicker(function: suspend DeckPicker.() -> Unit) =
        runTest {
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                composeTestRule.waitForIdle()
                var deckPicker: DeckPicker? = null
                scenario.onActivity { activity ->
                    deckPicker = activity
                }
                assertNotNull(deckPicker).function()
                composeTestRule.waitForIdle()
            }
        }
}
