// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class ScopedTestViewModel : ViewModel() {
    var cleared: Boolean = false
        private set

    override fun onCleared() {
        cleared = true
    }
}

@RunWith(AndroidJUnit4::class)
class NavigationStateTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun singletonNavKeysHaveStableDataObjectToString() {
        val singletons =
            listOf(
                DeckPickerScreen,
                HelpScreen,
                ContributeScreen,
                StatisticsDestination,
                ManageNoteTypesDestination,
            )

        singletons.forEach { key ->
            assertFalse(
                "Expected data object toString without JVM identity hash for $key",
                key.toString().contains("@"),
            )
            assertEquals(key::class.simpleName, key.toString())
        }
    }

    @Test
    fun navKeySerializerRoundTripsAllAppNavKeys() {
        val serializer = NavKeySerializer<NavKey>()
        val keys: List<NavKey> =
            listOf(
                DeckPickerScreen,
                HelpScreen,
                ContributeScreen,
                CongratsScreen(deckId = 1234L),
                StatisticsDestination,
                DeckOptionsDestination(deckId = 5678L),
                CardInfoDestination(cardId = 9012L),
                ManageNoteTypesDestination,
            )

        keys.forEach { original ->
            val encoded = Json.encodeToString(serializer, original)
            val decoded = Json.decodeFromString(serializer, encoded)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun toEntriesScopesAndClearsViewModelOnPop() {
        lateinit var navigator: Navigator
        val createdViewModels = mutableListOf<ScopedTestViewModel>()

        composeTestRule.setContent {
            val navigationState =
                rememberNavigationState(
                    startRoute = DeckPickerScreen,
                    topLevelRoutes = setOf(DeckPickerScreen),
                )
            navigator = remember(navigationState) { Navigator(navigationState) }

            val provider =
                entryProvider {
                    entry<DeckPickerScreen> {
                        Text("Root")
                    }
                    entry<HelpScreen> {
                        val vm: ScopedTestViewModel = viewModel()
                        LaunchedEffect(vm) {
                            createdViewModels.add(vm)
                        }
                        Text("Help")
                    }
                }

            NavDisplay(
                entries = navigator.state.toEntries(provider),
                onBack = { navigator.goBack() },
            )
        }

        composeTestRule.onNodeWithText("Root").assertIsDisplayed()

        composeTestRule.runOnIdle { navigator.navigate(HelpScreen) }
        composeTestRule.onNodeWithText("Help").assertIsDisplayed()
        assertEquals(1, createdViewModels.size)
        val firstVm = createdViewModels.first()
        assertFalse(firstVm.cleared)

        composeTestRule.runOnIdle { navigator.goBack() }
        composeTestRule.onNodeWithText("Root").assertIsDisplayed()
        composeTestRule.waitForIdle()
        assertTrue("Expected ViewModel to be cleared when Nav3 entry is popped", firstVm.cleared)

        composeTestRule.runOnIdle { navigator.navigate(HelpScreen) }
        composeTestRule.onNodeWithText("Help").assertIsDisplayed()
        assertEquals(2, createdViewModels.size)
        val secondVm = createdViewModels.last()
        assertNotSame(firstVm, secondVm)
        assertFalse(secondVm.cleared)
    }

    @Test
    fun hoistedNavigationStateSurvivesConditionalHostUnmount() {
        var isOpen by mutableStateOf(true)
        lateinit var navigator: Navigator

        composeTestRule.setContent {
            val navigationState =
                rememberNavigationState(
                    startRoute = DeckPickerScreen,
                    topLevelRoutes = setOf(DeckPickerScreen),
                )
            navigator = remember(navigationState) { Navigator(navigationState) }

            if (!isOpen) return@setContent

            val provider =
                entryProvider {
                    entry<DeckPickerScreen> { Text("Root") }
                    entry<HelpScreen> { Text("Help") }
                }

            NavDisplay(
                entries = navigator.state.toEntries(provider),
                onBack = { navigator.goBack() },
            )
        }

        composeTestRule.runOnIdle { navigator.navigate(HelpScreen) }
        composeTestRule.onNodeWithText("Help").assertIsDisplayed()

        composeTestRule.runOnIdle { isOpen = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Help").assertDoesNotExist()

        composeTestRule.runOnIdle { isOpen = true }
        composeTestRule.onNodeWithText("Help").assertIsDisplayed()
        assertEquals(HelpScreen, navigator.currentRoute)
    }

    @Test
    fun rememberNavigationStateRestoresTwoTopLevelStacksAcrossStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navigationState: NavigationState
        lateinit var navigator: Navigator

        restorationTester.setContent {
            navigationState =
                rememberNavigationState(
                    startRoute = DeckPickerScreen,
                    topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
                )
            navigator = remember(navigationState) { Navigator(navigationState) }

            val provider =
                entryProvider {
                    entry<DeckPickerScreen> { Text("Decks") }
                    entry<HelpScreen> { Text("Help") }
                    entry<StatisticsDestination> { Text("Stats") }
                    entry<ContributeScreen> { Text("Contribute") }
                }

            NavDisplay(
                entries = navigationState.toEntries(provider),
                onBack = { navigator.goBack() },
            )
        }

        composeTestRule.runOnIdle {
            navigator.navigate(HelpScreen)
            navigator.navigate(StatisticsDestination)
            navigator.navigate(ContributeScreen)
        }
        composeTestRule.onNodeWithText("Contribute").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Contribute").assertIsDisplayed()
        assertEquals(StatisticsDestination, navigationState.topLevelRoute)
        assertEquals(
            listOf<NavKey>(DeckPickerScreen, HelpScreen),
            navigationState.backStacks[DeckPickerScreen]?.toList(),
        )
        assertEquals(
            listOf<NavKey>(StatisticsDestination, ContributeScreen),
            navigationState.backStacks[StatisticsDestination]?.toList(),
        )
    }
}
