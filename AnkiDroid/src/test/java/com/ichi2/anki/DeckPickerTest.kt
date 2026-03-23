// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageButton
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // Prevent BackupPromptDialog Compose overlay from blocking tests.
        // In Robolectric, getFirstInstallTime() returns 0 (epoch), making
        // the user appear non-new, so the dialog would otherwise show.
        targetContext.sharedPrefs()
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
                    deckPicker.viewModel.dueTree!!.children[0].newCount.toLong(),
                )
            }
        }
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() = runTest {
        val did = addDeck("Hello World")
        assertThat("Deck was added", col.decks.count(), equalTo(2))
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            var job: Job? = null
            scenario.onActivity {
                job = it.viewModel.deleteDeck(did)
            }
            job?.join()
            composeTestRule.waitForIdle()
        }
        assertThat("deck was deleted", col.decks.count(), equalTo(1))
    }

    @Test
    fun databaseLockedTest() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            scenario.onActivity { deckPicker ->
                deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
                val dialogFragment =
                    deckPicker.supportFragmentManager.fragments.firstOrNull { it is DatabaseErrorDialog } as? DatabaseErrorDialog
                assertNotNull(dialogFragment)
                val dialogType = BundleCompat.getParcelable(
                    dialogFragment.requireArguments(), "dialog", DatabaseErrorDialogType::class.java
                )
                assertEquals(DatabaseErrorDialogType.DIALOG_DB_LOCKED, dialogType)
            }
        }
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sentExceptionReportHack = false
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                composeTestRule.waitForIdle()
                scenario.onActivity { d ->
                    val dialogFragment =
                        d.supportFragmentManager.fragments.firstOrNull { it is DatabaseErrorDialog } as? DatabaseErrorDialog
                    assertNotNull(dialogFragment)
                    val dialogType = BundleCompat.getParcelable(
                        dialogFragment.requireArguments(),
                        "dialog",
                        DatabaseErrorDialogType::class.java
                    )
                    assertEquals(DatabaseErrorDialogType.DIALOG_DB_LOCKED, dialogType)
                    assertEquals(false, AnkiDroidApp.sentExceptionReportHack)
                }
            }
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun deckPickerOpensWithHelpMakeAnkiDroidBetterDialog() = deckPicker {
        try {
            grantWritePermissions()
            targetContext.sharedPrefs().edit { putString("lastVersion", "0.1") }

            // Recreate to trigger dialog since deckPicker already launched it
            ActivityScenario.launch(DeckPicker::class.java).use {
                composeTestRule.waitForIdle()
                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull(dialog, "Analytics opt-in should be displayed")
            }
        } finally {
            revokeWritePermissions()
        }
    }


    // TODO: enableNullCollection() causes BackendDbLockedException when DeckPicker startup
    //  coroutines try to open the collection. The exception propagates before the options
    //  menu can be inspected. Fix: use a mechanism that delays the lock until after startup.
    @Ignore("BackendDbLockedException thrown during startup when null collection is enabled")
    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() = runTest {
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
    fun showOptionsMenuWhenCollectionAccessible() = runTest {
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

                    val expectedSyncLabel = when (activity.optionsMenuState?.syncIcon) {
                        SyncIconState.OneWay -> targetContext.getString(R.string.sync_menu_title_one_way_sync)
                        SyncIconState.NotLoggedIn -> targetContext.getString(R.string.sync_menu_title_no_account)
                        SyncIconState.PendingChanges,
                        SyncIconState.Normal,
                        null,
                            -> targetContext.getString(R.string.button_sync)
                    }

                    val syncButton =
                        menu.findItem(R.id.action_sync).actionView?.findViewById<AppCompatImageButton>(
                            R.id.button
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
                    assertFailsWith<BackendDbLockedException> { d.getColUnsafe }
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
    @Ignore("ShadowDialog.getLatestDialog() returns null — dialogs now shown via Compose/FragmentManager")
    @Test
    fun `ContextMenu starts expected dialogs when specific options are selected`() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val didA = addDeck("Deck 1")

            supportFragmentManager.selectContextMenuOption(
                DeckPickerContextMenuOption.RENAME_DECK, didA
            )
            assertDialogTitleEquals("Rename deck")
            dismissAllDialogFragments()

            supportFragmentManager.selectContextMenuOption(
                DeckPickerContextMenuOption.CREATE_SUBDECK, didA
            )
            assertDialogTitleEquals("Create subdeck")
            dismissAllDialogFragments()

            supportFragmentManager.selectContextMenuOption(
                DeckPickerContextMenuOption.CUSTOM_STUDY, didA
            )
            assertDialogTitleEquals("Custom study")
            dismissAllDialogFragments()

//            TODO test code enters in a recursion in BasicItemSelectedListener inside ExportDialog
//            supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.EXPORT_DECK, didA)
//            assertAlertDialogTitleEquals("Export")
//            dismissAllDialogFragments()
        }
    }

    /** Simulates a selection in the context menu by setting the specific result in FragmentManager */
    private fun FragmentManager.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) {
        val arguments = Bundle().apply {
            putLong(DeckPickerContextMenu.CONTEXT_MENU_DECK_ID, deckId)
            putSerializable(DeckPickerContextMenu.CONTEXT_MENU_DECK_OPTION, option)
        }
        setFragmentResult(DeckPickerContextMenu.REQUEST_KEY_CONTEXT_MENU, arguments)
    }

    private fun assertDialogTitleEquals(expectedTitle: String) {
        val actualTitle = (ShadowDialog.getLatestDialog() as AlertDialog).title
        Timber.d("titles = \"$actualTitle\", \"$expectedTitle\"")
        assertEquals(expectedTitle, actualTitle)
    }

    @Test
    fun `ContextMenu starts deck options for normal deck`() = deckPicker {
        val didA = addDeck("Deck 1")
        viewModel.openDeckOptions(didA).join()
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        val deckOptionsNormal = Shadows.shadowOf(this).nextStartedActivity
        assertNotNull(deckOptionsNormal)
        assertEquals(
            "com.ichi2.anki.SingleFragmentActivity", deckOptionsNormal.component!!.className
        )
    }

    @Test
    fun `ContextMenu starts deck options for dynamic deck`() = deckPicker {
        val didDynamicA = addDynamicDeck("Deck Dynamic 1")
        viewModel.openDeckOptions(didDynamicA).join()
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()
        val deckOptionsDynamic = Shadows.shadowOf(this).nextStartedActivity
        assertNotNull(deckOptionsDynamic)
        assertEquals("com.ichi2.anki.FilteredDeckOptions", deckOptionsDynamic.component!!.className)
    }

    @Ignore("Schedule reminders is not currently exposed from the Compose deck row UI")
    @Test
    fun `ContextMenu starts schedule reminders activity`() = deckPicker {
        error("Obsolete test body")
    }


    @Test
    fun `ContextMenu deletes deck when selecting DELETE_DECK`() = deckPicker {
        val didA = addDeck("Deck 1")
        viewModel.deleteDeck(didA).join()
        composeTestRule.waitForIdle()
        ShadowLooper.idleMainLooper()

        assertThat(
            getColUnsafe.decks.allNamesAndIds().map { it.id }, not(containsInAnyOrder(didA))
        )
    }

    @Ignore("Create shortcut is not currently exposed from the Compose deck row UI")
    @Test
    fun `ContextMenu creates deck shortcut when selecting CREATE_SHORTCUT`() = deckPicker {
        error("Obsolete test body")
    }

    // TODO: UncaughtExceptionsBeforeTest — leaked coroutine exceptions from previous tests
    //  pollute this test's TestScope. Also marked @Flaky on all OS.
    @Ignore("UncaughtExceptionsBeforeTest: leaked coroutine exceptions from DeckPicker startup")
    @Test
    @Flaky(OS.ALL)
    fun `ContextMenu unburied cards when selecting UNBURY`() = runTest {
        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            TimeManager.reset()
            // stop 'next day' code running, which calls 'unbury'
            updateDeckList()
            val deckId = addDeck("Deck 1")
            getColUnsafe.decks.select(deckId)
            getColUnsafe.notetypes.byName("Basic")!!.did = deckId
            val card = addBasicNote("front", "back").firstCard()
            getColUnsafe.sched.buryCards(listOf(card.id))
            updateDeckList()
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            assertEquals(1, viewModel.flowOfDeckList.first().data.size)
            assertTrue(getColUnsafe.sched.haveBuried(), "Deck should have buried cards")
            supportFragmentManager.selectContextMenuOption(
                DeckPickerContextMenuOption.UNBURY, deckId
            )
            assertFalse(getColUnsafe.sched.haveBuried())
        }
    }

    @Test
    fun `ContextMenu testDynRebuildAndEmpty`() = deckPicker {
        val cardIds = (0..3).map { addBasicNote("$it", "").firstCard().id }
        assertTrue(allCardsInSameDeck(cardIds, 1))
        val deckId = addDynamicDeck("Deck 1")
        getColUnsafe.sched.rebuildFilteredDeck(deckId)
        assertTrue(allCardsInSameDeck(cardIds, deckId))
        viewModel.emptyFilteredDeck(deckId).join()

        assertTrue(allCardsInSameDeck(cardIds, 1))

        viewModel.rebuildFilteredDeck(deckId).join()

        assertTrue(allCardsInSameDeck(cardIds, deckId))
    }

    private fun allCardsInSameDeck(
        cardIds: List<Long>,
        deckId: DeckId,
    ): Boolean = cardIds.all { col.getCard(it).did == deckId }

    @Test
    fun checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() = runTest {
        addDeck("Hello World")
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", col.decks.count(), equalTo(2))
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            var inInitialState: Boolean? = null
            scenario.onActivity { inInitialState = it.viewModel.flowOfDeckListInInitialState.value }
            assertThat(
                "Deck is being displayed",
                inInitialState,
                equalTo(false),
            )
        }
    }

    @Test
    fun checkIfReturnsFalseWhenNoDeckIsDisplayed() = runTest {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", col.decks.count(), equalTo(1))
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            advanceUntilIdle()
            ShadowLooper.idleMainLooper()
            var inInitialState: Boolean? = null
            scenario.onActivity { inInitialState = it.viewModel.flowOfDeckListInInitialState.value }
            assertThat(
                "No deck is being displayed",
                inInitialState,
                equalTo(true),
            )
        }
    }

    @Ignore("TODO: Reimplement with Compose testing - commented-out assertions for haveBuried() and focusedDeck")
    @Test
    fun `unbury is usable - Issue 15050`() {
        // We had an issue where 'Unbury' was not visible
        // This was because the deck selection was not changed when a long press occurred

        // one empty deck to be initially selected, one with cards to check 'unbury' status
        val emptyDeck = addDeck("No Cards")
        val deckWithCards = addDeck("With Cards")
        updateDeckConfig(deckWithCards) { new.bury = true }

        // Add a note with 2 cards in deck "With Cards", one of these cards is to be buried
        col.notetypes.byName("Basic (and reversed card)")!!.also { noteType ->
            col.notetypes.save(noteType.apply { did = deckWithCards })
        }
        addBasicAndReversedNote()

        // Answer 'Easy' for one of the cards, burying the other
        col.decks.select(deckWithCards)
        col.sched.deckDueTree() // ? if not called, decks.select(toSelect) un-buries a card
        col.sched.answerCard(col.sched.card!!, Rating.EASY)
        assertThat("the other card is buried", col.sched.card, nullValue())

        // select a deck with no cards
        col.decks.select(emptyDeck)
        assertThat("unbury is not visible: deck has no cards", !col.sched.haveBuried())

        deckPicker {
            assertThat("deck focus is set", viewModel.focusedDeck, equalTo(emptyDeck))

            // ACT: open up the Deck Context Menu
            // Interaction with RecyclerView is removed as it's replaced by Compose
            // val deckToClick =
            //    recyclerView.children.single {
            //        it.findViewById<TextView>(R.id.deckpicker_name).text == "With Cards"
            //    }
            // deckToClick.performLongClick()

            // ASSERT
            // assertThat("unbury is visible: one card is buried", col.sched.haveBuried())
            // assertThat("deck focus has changed", viewModel.focusedDeck, equalTo(deckWithCards))
        }
    }

    // TODO: menu().findItem(R.id.action_undo) returns null because the options menu is not
    //  inflated in Robolectric after Compose migration. The menu is now managed by the
    //  Compose NavHost rather than onCreateOptionsMenu. Fix: test undo state via ViewModel.
    @Ignore("Options menu not inflated in Robolectric — menu now managed by Compose NavHost")
    @Test
    @NeedsTest("possible bug: Moving the ops outside the deckPicker { } failed in tablet mode")
    fun `undo menu item changes`() = runTest {
        fun DeckPicker.getUndoTitle() = menu().findItem(R.id.action_undo).title.toString()

        fun waitForMenu() = ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        suspend fun DeckPicker.undo() {
            undoAndShowSnackbar()
            waitForMenu()
        }

        deckPicker {
            // enqueue two actions, neither of which affect the study queues
            val note = addBasicNoteWithOp()
            note.updateOp { this.fields[0] = "baz" }

            waitForMenu()
            assertThat(getUndoTitle(), containsString("Update Note"))
            undo()
            assertThat(getUndoTitle(), containsString("Add Note"))
        }
    }

    // TODO: deckPicker {} helper triggers full DeckPicker Compose lifecycle. The startup
    //  runnables on the main looper deadlock with runTest's TestCoroutineScheduler.
    @Ignore("Hangs: DeckPicker Compose startup runnables deadlock with runTest scheduler")
    @Test
    fun `On a new startup, the App Intro is displayed`() {
        setIntroductionSlidesShown(false)

        deckPicker {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "App Intro should be started on a new startup",
                nextIntent.component?.className,
                equalTo(IntroductionActivity::class.java.name),
            )
        }
    }

    // TODO: deckPicker {} helper triggers full DeckPicker Compose lifecycle, causing
    //  the same main looper / TestCoroutineScheduler deadlock as the other deckPicker tests.
    @Ignore("Hangs: DeckPicker Compose startup runnables deadlock with runTest scheduler")
    @Test
    fun `On not a new startup, the App Intro is not displayed`() {
        setIntroductionSlidesShown(true)

        deckPicker {
            val nextIntent = Shadows.shadowOf(this).nextStartedActivity

            assertThat(
                "No other activity should be started when not a new startup",
                nextIntent,
                equalTo(null),
            )
        }
    }

    private fun deckPicker(function: suspend DeckPicker.() -> Unit) = runTest {
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

    private fun setIntroductionSlidesShown(shown: Boolean) {
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, shown)
        }
    }


}
