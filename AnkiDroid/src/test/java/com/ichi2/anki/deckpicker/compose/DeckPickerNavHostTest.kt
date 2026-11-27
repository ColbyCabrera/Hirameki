// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.deckpicker.compose

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.CardBrowserActionHandler
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.navigation.CongratsScreen
import com.ichi2.anki.navigation.ContributeScreen
import com.ichi2.anki.navigation.DeckPickerScreen
import com.ichi2.anki.navigation.HelpScreen
import com.ichi2.anki.navigation.ManageNoteTypesDestination
import com.ichi2.anki.navigation.Navigator
import com.ichi2.anki.navigation.rememberNavigationState
import com.ichi2.anki.navigation.toEntries
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
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
    fun navHostUpdatesRenderedEntryWhenFragmentedChangesWithoutBackStackChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val openDrawerDescription = context.getString(R.string.navigation_drawer_open)

        val viewModel = DeckPickerViewModel()
        val cardBrowserViewModel = createMockBrowserViewModel()
        val actionHandler = mockk<CardBrowserActionHandler>(relaxed = true)
        var fragmented by mutableStateOf(false)

        composeTestRule.setContent {
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

        composeTestRule.runOnIdle { fragmented = true }
        composeTestRule.waitForIdle()

        // In fragmented tablet mode, the navigation icon is hidden because the navigation rail is shown.
        composeTestRule.onNodeWithContentDescription(openDrawerDescription).assertDoesNotExist()

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
}
