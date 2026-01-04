/****************************************************************************************
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                       *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                                 *
 * See the GNU General Public License for more details.                                 *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

/**
 * Create a navigation state that persists config changes and process death.
 */
@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>
): NavigationState {

    val topLevelRoute = rememberSerializable(
        startRoute, topLevelRoutes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutes) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks
        )
    }
}

/**
 * State holder for navigation state.
 *
 * @param startRoute - the start route. The user will exit the app through this route.
 * @param topLevelRoute - the current top level route
 * @param backStacks - the back stacks for each top level route
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var topLevelRoute: NavKey by topLevelRoute
    val stacksInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

/**
 * Convert NavigationState into NavEntries.
 */
@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>
): SnapshotStateList<NavEntry<NavKey>> {

    val decoratedEntries = backStacks.mapValues { (_, stack) ->
        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        )
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )
    }

    return stacksInUse
        .flatMap { decoratedEntries[it] ?: emptyList() }
        .toMutableStateList()
}
