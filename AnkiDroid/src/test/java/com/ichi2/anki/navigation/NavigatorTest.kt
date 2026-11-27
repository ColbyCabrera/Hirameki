// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigatorTest {
    private fun createNavigator(
        startRoute: NavKey = DeckPickerScreen,
        topLevelRoutes: Set<NavKey> = setOf(DeckPickerScreen),
    ): Pair<NavigationState, Navigator> {
        val state =
            NavigationState(
                startRoute = startRoute,
                topLevelRoute = mutableStateOf(startRoute),
                backStacks = topLevelRoutes.associateWith { NavBackStack(it) },
            )
        return state to Navigator(state)
    }

    @Test
    fun `navigate pushes child route onto active stack`() {
        val (state, navigator) = createNavigator()

        navigator.navigate(HelpScreen)

        assertEquals(listOf<NavKey>(DeckPickerScreen, HelpScreen), state.backStacks[DeckPickerScreen]?.toList())
        assertEquals(HelpScreen, navigator.currentRoute)
    }

    @Test
    fun `navigate ignores consecutive duplicate route pushes`() {
        val (state, navigator) = createNavigator()

        navigator.navigate(HelpScreen)
        navigator.navigate(HelpScreen)

        assertEquals(listOf<NavKey>(DeckPickerScreen, HelpScreen), state.backStacks[DeckPickerScreen]?.toList())
    }

    @Test
    fun `navigate deduplicates non-consecutive route by moving it to top`() {
        val (state, navigator) = createNavigator()

        navigator.navigate(HelpScreen)
        navigator.navigate(ContributeScreen)
        navigator.navigate(HelpScreen)

        assertEquals(
            listOf<NavKey>(DeckPickerScreen, ContributeScreen, HelpScreen),
            state.backStacks[DeckPickerScreen]?.toList(),
        )
        assertEquals(HelpScreen, navigator.currentRoute)
    }

    @Test
    fun `navigate distinguishes parameterized routes with different arguments`() {
        val (state, navigator) = createNavigator()

        navigator.navigate(CongratsScreen(deckId = 1L))
        navigator.navigate(CongratsScreen(deckId = 2L))
        navigator.navigate(CongratsScreen(deckId = 2L))

        assertEquals(
            listOf<NavKey>(DeckPickerScreen, CongratsScreen(1L), CongratsScreen(2L)),
            state.backStacks[DeckPickerScreen]?.toList(),
        )
    }

    @Test
    fun `navigate switches between top-level stacks while retaining child history`() {
        val (state, navigator) =
            createNavigator(
                startRoute = DeckPickerScreen,
                topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
            )

        navigator.navigate(HelpScreen)
        navigator.navigate(StatisticsDestination)
        navigator.navigate(ContributeScreen)

        assertEquals(StatisticsDestination, state.topLevelRoute)
        assertEquals(ContributeScreen, navigator.currentRoute)
        assertEquals(listOf<NavKey>(DeckPickerScreen, HelpScreen), state.backStacks[DeckPickerScreen]?.toList())
        assertEquals(listOf<NavKey>(StatisticsDestination, ContributeScreen), state.backStacks[StatisticsDestination]?.toList())

        navigator.navigate(DeckPickerScreen)
        assertEquals(DeckPickerScreen, state.topLevelRoute)
        assertEquals(HelpScreen, navigator.currentRoute)
    }

    @Test
    fun `reselecting active top-level route pops back to its root`() {
        val (state, navigator) =
            createNavigator(
                startRoute = DeckPickerScreen,
                topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
            )

        navigator.navigate(HelpScreen)
        navigator.navigate(ContributeScreen)

        navigator.navigate(DeckPickerScreen)

        assertEquals(listOf<NavKey>(DeckPickerScreen), state.backStacks[DeckPickerScreen]?.toList())
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    fun `goBack pops child routes then returns from non-start top-level stack to start route`() {
        val (state, navigator) =
            createNavigator(
                startRoute = DeckPickerScreen,
                topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
            )

        navigator.navigate(StatisticsDestination)
        navigator.navigate(HelpScreen)

        navigator.goBack()
        assertEquals(StatisticsDestination, navigator.currentRoute)
        assertEquals(StatisticsDestination, state.topLevelRoute)

        navigator.goBack()
        assertEquals(DeckPickerScreen, state.topLevelRoute)
        assertEquals(DeckPickerScreen, navigator.currentRoute)

        // Calling goBack at start root is a safe no-op
        navigator.goBack()
        assertEquals(DeckPickerScreen, state.topLevelRoute)
        assertEquals(listOf<NavKey>(DeckPickerScreen), state.backStacks[DeckPickerScreen]?.toList())
    }

    @Test
    fun `goBack does not throw when active stack is empty`() {
        val state =
            NavigationState(
                startRoute = DeckPickerScreen,
                topLevelRoute = mutableStateOf(DeckPickerScreen),
                backStacks = mapOf(DeckPickerScreen to NavBackStack()),
            )
        val navigator = Navigator(state)

        assertNull(navigator.currentRoute)
        navigator.goBack()
        assertNull(navigator.currentRoute)
    }

    @Test
    fun `popIf pops only when current route matches`() {
        val (_, navigator) = createNavigator()

        navigator.navigate(CongratsScreen(deckId = 7L))

        assertFalse(navigator.popIf<HelpScreen>())
        assertEquals(CongratsScreen(deckId = 7L), navigator.currentRoute)

        assertTrue(navigator.popIf<CongratsScreen>())
        assertEquals(DeckPickerScreen, navigator.currentRoute)

        assertFalse(navigator.popIf<CongratsScreen>())
    }

    @Test
    fun `navigate deduplicates non-top-level route across stacks to keep contentKeys unique`() {
        val (state, navigator) =
            createNavigator(
                startRoute = DeckPickerScreen,
                topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
            )

        navigator.navigate(HelpScreen)
        navigator.navigate(StatisticsDestination)
        navigator.navigate(HelpScreen)

        assertEquals(listOf<NavKey>(DeckPickerScreen), state.backStacks[DeckPickerScreen]?.toList())
        assertEquals(listOf<NavKey>(StatisticsDestination, HelpScreen), state.backStacks[StatisticsDestination]?.toList())
        assertEquals(HelpScreen, navigator.currentRoute)
    }

    @Test
    fun `navigate with resetToRoot switches stack and pops back to root`() {
        val (state, navigator) =
            createNavigator(
                startRoute = DeckPickerScreen,
                topLevelRoutes = setOf(DeckPickerScreen, StatisticsDestination),
            )

        navigator.navigate(HelpScreen)
        navigator.navigate(StatisticsDestination)

        navigator.navigate(DeckPickerScreen, resetToRoot = true)

        assertEquals(DeckPickerScreen, state.topLevelRoute)
        assertEquals(listOf<NavKey>(DeckPickerScreen), state.backStacks[DeckPickerScreen]?.toList())
        assertEquals(DeckPickerScreen, navigator.currentRoute)
    }

    @Test
    fun `navigate and goBack fall back to startRoute if topLevelRoute is missing from backStacks`() {
        val state =
            NavigationState(
                startRoute = DeckPickerScreen,
                topLevelRoute = mutableStateOf(StatisticsDestination),
                backStacks = mapOf(DeckPickerScreen to NavBackStack(DeckPickerScreen)),
            )
        val navigator = Navigator(state)

        navigator.goBack()
        assertEquals(DeckPickerScreen, state.topLevelRoute)

        state.topLevelRoute = StatisticsDestination
        navigator.navigate(HelpScreen)
        assertEquals(DeckPickerScreen, state.topLevelRoute)
        assertEquals(listOf<NavKey>(DeckPickerScreen, HelpScreen), state.backStacks[DeckPickerScreen]?.toList())
    }
}
