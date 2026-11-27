// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(
    val state: NavigationState,
) {
    /**
     * The active route at the top of the currently selected back stack, if any.
     */
    val currentRoute: NavKey?
        get() = resolveActiveStack()?.lastOrNull()

    /**
     * Navigate to the specified route.
     * - If [route] is a top-level route and already active (or [resetToRoot] is `true`), switches
     *   to [route] and pops that stack back to its root.
     * - If [route] is another top-level route and [resetToRoot] is `false`, switches to that stack
     *   while preserving its history.
     * - Otherwise, pushes [route] onto the active stack while removing any existing occurrences of
     *   [route] across all stacks so Navigation 3's default content keys remain unique across
     *   concatenated [NavigationState.stacksInUse].
     */
    fun navigate(
        route: NavKey,
        resetToRoot: Boolean = false,
    ) {
        if (route in state.backStacks.keys) {
            val shouldReset = resetToRoot || state.topLevelRoute == route
            state.topLevelRoute = route
            if (shouldReset) {
                val stack = state.backStacks[route] ?: return
                while (stack.size > 1) {
                    stack.removeLastOrNull()
                }
            }
        } else {
            val currentStack = resolveActiveStack() ?: return
            // Remove any non-root occurrence of route in other stacks so stacksInUse never yields
            // duplicate contentKeys to NavDisplay.
            state.backStacks.forEach { (topLevelKey, stack) ->
                if (topLevelKey != state.topLevelRoute && route != topLevelKey) {
                    stack.removeAll { it == route }
                }
            }
            if (currentStack.lastOrNull() == route) {
                return
            }
            if (route in currentStack) {
                currentStack.removeAll { it == route }
            }
            currentStack.add(route)
        }
    }

    /**
     * Navigate back. If at the base of a non-start top-level stack, returns to [NavigationState.startRoute].
     */
    fun goBack() {
        val currentStack = resolveActiveStack() ?: return
        val currentRoute = currentStack.lastOrNull() ?: return

        // If we're at the base of the current route, go back to the start route stack.
        if (currentRoute == state.topLevelRoute) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
            }
        } else {
            currentStack.removeLastOrNull()
        }
    }

    /**
     * Resolves the stack for [NavigationState.topLevelRoute], falling back to
     * [NavigationState.startRoute] if a restored top-level route is no longer present.
     */
    private fun resolveActiveStack(): NavBackStack<NavKey>? {
        state.backStacks[state.topLevelRoute]?.let { return it }
        if (state.topLevelRoute != state.startRoute) {
            state.topLevelRoute = state.startRoute
        }
        return state.backStacks[state.startRoute]
    }

    /**
     * Pops the current route if it satisfies [predicate].
     *
     * @return `true` if the route matched and [goBack] was invoked.
     */
    fun popIf(predicate: (NavKey) -> Boolean): Boolean {
        val route = currentRoute ?: return false
        if (!predicate(route)) {
            return false
        }
        goBack()
        return true
    }

    /**
     * Pops the current route if it is an instance of [T].
     */
    inline fun <reified T : NavKey> popIf(): Boolean = popIf { it is T }
}
