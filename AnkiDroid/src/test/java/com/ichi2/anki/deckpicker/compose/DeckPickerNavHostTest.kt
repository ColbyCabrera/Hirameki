// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.deckpicker.compose

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.CardBrowserActionHandler
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.navigation.CardBrowserScreen
import com.ichi2.anki.navigation.CongratsScreen
import com.ichi2.anki.navigation.ContributeScreen
import com.ichi2.anki.navigation.DeckPickerScreen
import com.ichi2.anki.navigation.HelpScreen
import com.ichi2.anki.navigation.ManageNoteTypesDestination
import com.ichi2.anki.navigation.Navigator
import com.ichi2.anki.navigation.StatisticsDestination
import com.ichi2.anki.navigation.rememberNavigationState
import com.ichi2.anki.navigation.toEntries
import com.ichi2.anki.ui.compose.navigation.AppNavigationItem
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.testutils.createTransientDirectory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.ichi2.anki.ui.compose.CongratsScreen as CongratsComposable

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class DeckPickerNavHostTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMockBrowserViewModel(): CardBrowserViewModel =
        mockk(relaxed = true) {
            every { flowOfSnackbarMessage } returns MutableSharedFlow()
        }

    @Test
    fun congratsScreenDelegatesSystemBackToNavDisplay() {
        var navDisplayOnBackCount = 0
        var congratsOnNavigateUpCount = 0
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val navigationState =
                rememberNavigationState(
                    startRoute = DeckPickerScreen,
                    topLevelRoutes = setOf(DeckPickerScreen),
                )
            navigator = remember(navigationState) { Navigator(navigationState) }

            val provider =
                entryProvider {
                    entry<DeckPickerScreen> { Text("DeckPickerRoot") }
                    entry<CongratsScreen> {
                        CongratsComposable(
                            onNavigateUp = {
                                congratsOnNavigateUpCount++
                                navigator.goBack()
                            },
                            onDeckOptions = {},
                            onCustomStudy = {},
                            timeUntilNextDay = 10_000L,
                        )
                    }
                }

            NavDisplay(
                entries = navigator.state.toEntries(provider),
                onBack = {
                    navDisplayOnBackCount++
                    navigator.goBack()
                },
            )
        }

        composeTestRule.runOnIdle { navigator.navigate(CongratsScreen(deckId = 1L)) }
        composeTestRule.waitForIdle()
        assertEquals(CongratsScreen(deckId = 1L), navigator.currentRoute)

        composeTestRule.runOnIdle {
            checkNotNull(dispatcher).onBackPressed()
        }
        composeTestRule.waitForIdle()

        assertEquals(DeckPickerScreen, navigator.currentRoute)
        assertEquals(1, navDisplayOnBackCount)
        assertEquals(0, congratsOnNavigateUpCount)
    }

    @Test
    fun navHostNavigatesToHelpContributeAndCongratsAndPopsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backContentDescription = context.getString(R.string.back)
        val helpTitle = context.getString(R.string.help_screen_title)
        val contributeTitle = context.getString(R.string.contribute_screen_title)
        val congratsText = context.getString(R.string.studyoptions_congrats_finished)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel = createMockBrowserViewModel()
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = false,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.runOnIdle { navigator.navigate(HelpScreen) }
        composeTestRule.onNodeWithText(helpTitle).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backContentDescription).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)

        composeTestRule.runOnIdle { navigator.navigate(ContributeScreen) }
        composeTestRule.onNodeWithText(contributeTitle).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backContentDescription).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)

        composeTestRule.runOnIdle { navigator.navigate(CongratsScreen(deckId = 1L)) }
        composeTestRule.onNodeWithText(congratsText).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backContentDescription).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun navHostUpdatesRenderedEntryWhenFragmentedChangesWithoutBackStackChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openDrawerDescription = context.getString(R.string.navigation_drawer_open)
        val browserLabel = context.getString(R.string.browser)
        val searchDecksLabel = context.getString(R.string.search_decks)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel = createMockBrowserViewModel()
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var fragmented by mutableStateOf(false)

        composeTestRule.setContent {
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen, CardBrowserScreen),
                    )
                val navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = fragmented,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(openDrawerDescription).assertExists()
        // Populate remembered searchQuery inside DeckPickerMainContent before toggling fragmented.
        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText(searchDecksLabel).performTextInput("nihongo")
        composeTestRule.onNodeWithText("nihongo").assertIsDisplayed()

        composeTestRule.runOnIdle { fragmented = true }
        composeTestRule.waitForIdle()

        // In fragmented mode, the navigation rail appears and the drawer icon is hidden.
        composeTestRule.onNodeWithContentDescription(openDrawerDescription).assertDoesNotExist()
        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).assertIsDisplayed()
        // Re-opening search verifies DeckPickerMainContent preserved its searchQuery across the toggle.
        composeTestRule.onNodeWithContentDescription(searchDecksLabel).performClick()
        composeTestRule.onNodeWithText("nihongo").assertIsDisplayed()

        composeTestRule.runOnIdle { fragmented = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(openDrawerDescription).assertExists()
    }

    @Test
    fun backPressClosesOpenModalNavigationDrawer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openDrawerDescription = context.getString(R.string.navigation_drawer_open)
        val settingsLabel = context.getString(R.string.settings)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel = createMockBrowserViewModel()
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null

        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen),
                    )
                val navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = false,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(openDrawerDescription).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(settingsLabel).assertIsDisplayed()

        composeTestRule.runOnIdle {
            checkNotNull(dispatcher).onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(settingsLabel).assertIsNotDisplayed()
    }

    @Test
    fun openManageNoteTypesEffectNavigatesToInGraphRouteAndPopsBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manageNoteTypesTitle = context.getString(R.string.model_browser_label)
        val backContentDescription = context.getString(R.string.back)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel = createMockBrowserViewModel()
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = false,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.runOnIdle { viewModel.openManageNoteTypes() }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navigator.currentRoute == ManageNoteTypesDestination
        }
        composeTestRule.onNodeWithText(manageNoteTypesTitle).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(backContentDescription).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    fun selectedNavigationItemForRouteMapsAllRoutes() {
        assertEquals(AppNavigationItem.Decks, selectedNavigationItemForRoute(DeckPickerScreen, DeckPickerScreen))
        assertEquals(AppNavigationItem.CardBrowser, selectedNavigationItemForRoute(CardBrowserScreen, CardBrowserScreen))
        assertEquals(AppNavigationItem.Statistics, selectedNavigationItemForRoute(StatisticsDestination, DeckPickerScreen))
        assertEquals(AppNavigationItem.Help, selectedNavigationItemForRoute(HelpScreen, DeckPickerScreen))
        assertEquals(AppNavigationItem.Support, selectedNavigationItemForRoute(ContributeScreen, DeckPickerScreen))
        assertEquals(AppNavigationItem.Decks, selectedNavigationItemForRoute(CongratsScreen(1L), DeckPickerScreen))
        assertEquals(AppNavigationItem.Decks, selectedNavigationItemForRoute(ManageNoteTypesDestination, DeckPickerScreen))
        assertEquals(AppNavigationItem.CardBrowser, selectedNavigationItemForRoute(ManageNoteTypesDestination, CardBrowserScreen))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletRailSwitchesToCardBrowserStackStaysVisibleOnHelpAndDoesNotStickOnSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decksLabel = context.getString(R.string.decks)
        val browserLabel = context.getString(R.string.browser)
        val helpLabel = context.getString(R.string.help)
        val settingsLabel = context.getString(R.string.settings)
        val helpTitle = context.getString(R.string.help_screen_title)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = true,
                manualInit = true,
                savedStateHandle = SavedStateHandle(),
            )
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        val launchedIntents = mutableListOf<Intent>()
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen, CardBrowserScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = true,
                    onLaunchIntent = { launchedIntents += it },
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        // Initial state: Decks selected on the rail
        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).assertIsSelected()

        // Navigate to CardBrowser via the rail and verify back returns to Decks through Nav3
        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(CardBrowserScreen, navigator.currentRoute)
        assertEquals(CardBrowserScreen, navigator.state.topLevelRoute)
        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).assertIsSelected()

        composeTestRule.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
        assertEquals(DeckPickerScreen, navigator.state.topLevelRoute)
        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).assertIsSelected()

        // Navigate to Help via the rail: rail stays visible, highlights Help, and tapping Decks returns to root
        composeTestRule.onNode(hasText(helpLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(HelpScreen, navigator.currentRoute)
        composeTestRule.onNode(hasText(helpTitle) and isSelectable().not()).assertIsDisplayed()
        composeTestRule.onNode(hasText(helpLabel) and isSelectable()).assertIsSelected()

        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).assertIsSelected()

        // Tapping Settings launches an intent without leaving Settings selected on the rail
        composeTestRule.onNode(hasText(settingsLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, launchedIntents.size)
        composeTestRule.onNode(hasText(settingsLabel) and isSelectable()).assertIsNotSelected()
        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletRailClearsSecondaryRoutesAcrossBrowserAndAvoidsDuplicateHelpEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decksLabel = context.getString(R.string.decks)
        val browserLabel = context.getString(R.string.browser)
        val helpLabel = context.getString(R.string.help)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = true,
                manualInit = true,
                savedStateHandle = SavedStateHandle(),
            )
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen, CardBrowserScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = true,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        // Help -> Browser -> Decks must land on Decks root (not Help)
        composeTestRule.onNode(hasText(helpLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(HelpScreen, navigator.currentRoute)

        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(CardBrowserScreen, navigator.currentRoute)
        assertEquals(listOf<NavKey>(DeckPickerScreen), navigator.state.backStacks[DeckPickerScreen]?.toList())

        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
        composeTestRule.onNode(hasText(decksLabel) and isSelectable()).assertIsSelected()

        // Help -> Browser -> Help -> Back must not leave duplicate Help entries across stacks
        composeTestRule.onNode(hasText(helpLabel) and isSelectable()).performClick()
        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).performClick()
        composeTestRule.onNode(hasText(helpLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            listOf<NavKey>(DeckPickerScreen, HelpScreen),
            navigator.state.backStacks[DeckPickerScreen]?.toList(),
        )
        assertEquals(
            listOf<NavKey>(CardBrowserScreen),
            navigator.state.backStacks[CardBrowserScreen]?.toList(),
        )

        // Switching to Browser and pressing Back returns to Decks root
        composeTestRule.onNode(hasText(browserLabel) and isSelectable()).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun restoringCardBrowserStackInCompactModeFallsBackToDeckPicker() {
        val restorationTester = StateRestorationTester(composeTestRule)
        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = true,
                manualInit = true,
                savedStateHandle = SavedStateHandle(),
            )
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var fragmented by mutableStateOf(true)
        lateinit var navigator: Navigator

        restorationTester.setContent {
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen, CardBrowserScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = fragmented,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.runOnIdle { navigator.navigate(CardBrowserScreen) }
        composeTestRule.waitForIdle()
        assertEquals(CardBrowserScreen, navigator.state.topLevelRoute)

        // Emulate activity recreation into compact (non-fragmented) layout
        composeTestRule.runOnIdle { fragmented = false }
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(DeckPickerScreen, navigator.state.topLevelRoute)
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletBrowserBackClosesSearchBeforePoppingStack() {
        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                cacheDir = createTransientDirectory(),
                options = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = true,
                manualInit = true,
                savedStateHandle = SavedStateHandle(),
            )
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var dispatcher: androidx.activity.OnBackPressedDispatcher? = null
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AnkiDroidTheme {
                val navigationState =
                    rememberNavigationState(
                        startRoute = DeckPickerScreen,
                        topLevelRoutes = setOf(DeckPickerScreen, CardBrowserScreen),
                    )
                navigator = remember(navigationState) { Navigator(navigationState) }

                DeckPickerNavHost(
                    navigator = navigator,
                    viewModel = viewModel,
                    cardBrowserViewModel = cardBrowserViewModel,
                    actionHandler = actionHandler,
                    fragmented = true,
                    onLaunchIntent = {},
                    onAddNote = {},
                    onAddSharedDeck = {},
                    onAddFilteredDeck = {},
                    onShowDialogFragment = {},
                    onInvalidateOptionsMenu = {},
                    onLoginToAnkiWeb = {},
                    onLocalNetworkPermissionRequest = {},
                    onImport = {},
                    onExport = {},
                )
            }
        }

        composeTestRule.runOnIdle { navigator.navigate(CardBrowserScreen) }
        composeTestRule.waitForIdle()
        assertEquals(CardBrowserScreen, navigator.currentRoute)

        composeTestRule.runOnIdle { cardBrowserViewModel.expandSearchQuery() }
        composeTestRule.waitForIdle()
        assertTrue(cardBrowserViewModel.flowOfSearchQueryExpanded.value)

        // First back closes search while staying on CardBrowserScreen
        composeTestRule.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        composeTestRule.waitForIdle()
        assertFalse(cardBrowserViewModel.flowOfSearchQueryExpanded.value)
        assertEquals(CardBrowserScreen, navigator.currentRoute)

        // Second back pops CardBrowserScreen back to DeckPickerScreen via NavDisplay
        composeTestRule.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        composeTestRule.waitForIdle()
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }
}
