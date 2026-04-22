---
name: compose-navigation
description: "Implement app navigation in Jetpack Compose using Navigation 3 (Nav3). Use when asked to set up Nav3, define NavKey destinations, create NavigationState or Navigator classes, replace NavController/NavHost with entryProvider and NavDisplay, migrate from Navigation Compose, or structure multi-screen Compose apps."
---

# Compose Navigation

## Overview

Use Navigation 3 for new Compose navigation work in this repository.

Navigation 3 replaces `NavController` and `NavHost` with a model where you:

- Define strongly typed destinations as `NavKey`s.
- Hoist navigation state into a dedicated state holder.
- Update that state through a small navigator object.
- Resolve destinations through an `entryProvider`.
- Render the current back stack with `NavDisplay`.

This repo already has a small in-progress Nav3 implementation, so new guidance should follow that pattern instead of older Navigation Compose examples.

## Repository Alignment

Current repo Nav3 usage is centered around these files:

- `AnkiDroid/src/main/java/com/ichi2/anki/navigation/AppNavigation.kt`: typed `NavKey` destinations.
- `AnkiDroid/src/main/java/com/ichi2/anki/navigation/NavigationState.kt`: hoisted back stack state with `rememberNavigationState()` and `toEntries()`.
- `AnkiDroid/src/main/java/com/ichi2/anki/navigation/Navigator.kt`: simple navigation event handler.
- `AnkiDroid/src/main/java/com/ichi2/anki/deckpicker/compose/DeckPickerNavHost.kt`: `entryProvider` plus `NavDisplay` rendering.

Prefer extending those patterns over introducing `NavController`, `NavHost`, or string-route APIs in new code.

---

## Setup

Prefer the version catalog entries that already exist in this repo:

```toml
# gradle/libs.versions.toml
[versions]
nav3Core = "1.0.1"
lifecycleViewmodelNav3 = "2.10.0"

[libraries]
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
```

```kotlin
// module build.gradle.kts
plugins {
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
}
```

Official Nav3 docs currently require `compileSdk` 36 or newer for new setup. When changing navigation infrastructure, verify the module and repo SDK levels before recommending Nav3 rollout.

---

## Core Concepts

### 1. Define Destinations as `NavKey`

Use `@Serializable` keys that implement `NavKey`.

```kotlin
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeScreen : NavKey

@Serializable
data class ProfileScreen(val userId: Long) : NavKey

@Serializable
data class SettingsScreen(val section: String? = null) : NavKey
```

Prefer IDs and small primitives in keys. Do not put large mutable objects into navigation keys.

### 2. Hoist Navigation State

Navigation 3 works best when navigation state is explicit and observable.

```kotlin
@Composable
fun AppRoot() {
    val navigationState = rememberNavigationState(
        startRoute = HomeScreen,
        topLevelRoutes = setOf(HomeScreen, SettingsScreen())
    )
    val navigator = remember { Navigator(navigationState) }

    AppNavHost(navigator = navigator)
}
```

In this repo, `rememberNavigationState()` wraps `rememberNavBackStack()` and `rememberSerializable()` so back stacks survive recomposition, config changes, and process recreation.

### 3. Use a Small Navigator Object

Keep navigation mutations centralized.

```kotlin
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun goBack() {
        val stack = checkNotNull(state.backStacks[state.topLevelRoute])
        if (stack.last() == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            stack.removeLastOrNull()
        }
    }
}
```

Avoid scattering back stack mutations across random composables.

### 4. Resolve Destinations with `entryProvider`

Navigation 3 does not use `NavGraphBuilder` for primary destination setup.

```kotlin
val entryProvider = entryProvider {
    entry<HomeScreen> {
        HomeRoute(
            onOpenProfile = { userId -> navigator.navigate(ProfileScreen(userId)) }
        )
    }

    entry<ProfileScreen> { key ->
        ProfileRoute(
            userId = key.userId,
            onNavigateUp = { navigator.goBack() }
        )
    }

    entry<SettingsScreen> { key ->
        SettingsRoute(
            section = key.section,
            onNavigateUp = { navigator.goBack() }
        )
    }
}
```

Arguments come from the typed key passed into `entry<T> { key -> ... }`.

### 5. Render with `NavDisplay`

```kotlin
NavDisplay(
    entries = navigator.state.toEntries(entryProvider),
    onBack = { navigator.goBack() },
)
```

Use `transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`, and scene strategies only when the UX needs them.

---

## Preferred Repo Pattern

For this repository, prefer this architectural split:

1. `AppNavigation.kt`: destination key definitions.
2. `NavigationState.kt`: state holder and `toEntries()` helpers.
3. `Navigator.kt`: navigation event logic.
4. Feature host composable: feature-specific `entryProvider` and `NavDisplay`.
5. Screens: emit events upward, do not mutate navigation state directly.

This keeps navigation logic testable and aligned with UDF.

---

## Common Patterns

### Navigate Forward

```kotlin
navigator.navigate(ProfileScreen(userId = 42L))
```

### Navigate Back

```kotlin
navigator.goBack()
```

### Top-Level Navigation

Treat top-level destinations as separate stacks.

```kotlin
val topLevelRoutes = setOf(HomeScreen, SearchScreen, SettingsScreen())
val selected = navigationState.topLevelRoute == SearchScreen
```

In Nav3, selected state for bottom bars or rails should usually come from `navigationState.topLevelRoute`, not from a destination hierarchy query.

### Passing Arguments

```kotlin
@Serializable
data class CardInfoDestination(val cardId: Long) : NavKey

entry<CardInfoDestination> { key ->
    CardInfoRoute(cardId = key.cardId)
}
```

Pass IDs, enum-like values, and compact immutable data. Fetch heavy data from repositories or ViewModels.

### Dialogs and Metadata

If a destination should render as a dialog or another custom scene, use entry metadata and scene strategies rather than trying to recreate NavHost dialog APIs directly. Follow the official Navigation 3 metadata and scene strategy docs for this.

---

## Migration Advice

When migrating from Navigation Compose to Navigation 3, follow the official sequence:

1. Add Nav3 dependencies.
2. Convert routes to `NavKey`.
3. Create `NavigationState` and `Navigator`.
4. Replace `NavController` calls with navigator/state access.
5. Move destinations from `NavHost` into `entryProvider`.
6. Replace `NavHost` with `NavDisplay`.
7. Remove obsolete Navigation 2 dependencies and imports.

Important constraints from the current docs:

- Deep links are not covered by the official migration guide yet.
- More than one level of nested navigation is not covered by the migration guide.
- Shared destinations that move between stacks require custom navigation logic.

Do not casually promise one-to-one replacements for advanced Navigation Compose features. Check the official recipes first.

---

## Testing

Prefer testing navigation state and screen behavior rather than implementation details.

### State-Level Tests

Test `NavigationState` and `Navigator` as plain Kotlin logic where possible.

```kotlin
@Test
fun navigate_pushes_child_onto_current_stack() {
    val state = NavigationState(
        startRoute = HomeScreen,
        topLevelRoute = mutableStateOf(HomeScreen),
        backStacks = mapOf(
            HomeScreen to mutableStateListOf<NavKey>(HomeScreen),
            SettingsScreen() to mutableStateListOf<NavKey>(SettingsScreen())
        )
    )
    val navigator = Navigator(state)

    navigator.navigate(ProfileScreen(userId = 42L))

    assert(state.backStacks[HomeScreen]?.last() == ProfileScreen(userId = 42L))
}
```

### Compose UI Tests

In Compose tests, render the host with a real `NavigationState` and `Navigator`, then assert screen content after user interaction.

```kotlin
@Test
fun clicking_profile_opens_profile_screen() {
    composeTestRule.setContent {
        val state = rememberNavigationState(
            startRoute = HomeScreen,
            topLevelRoutes = setOf(HomeScreen)
        )
        val navigator = remember { Navigator(state) }

        AppNavHost(navigator = navigator)
    }

    composeTestRule.onNodeWithText("Open profile").performClick()
    composeTestRule.onNodeWithText("Profile").assertIsDisplayed()
}
```

For this repo specifically, prefer testing ViewModel state emission and navigation state transitions over checking internal Compose plumbing.

---

## Critical Rules

### DO

- Use `NavKey` plus `@Serializable` for destinations.
- Hoist navigation state out of leaf composables.
- Keep navigation writes in a dedicated navigator or equivalent state owner.
- Use `entryProvider` and `NavDisplay` for new Nav3 flows.
- Model top-level destinations as separate retained back stacks when the UX requires it.
- Test navigation state changes and user-visible results.

### DON'T

- Introduce `NavController` or `NavHost` for new Nav3 features.
- Use string route constants for new work.
- Pass repository models or other large mutable objects in keys.
- Mirror Navigation Compose APIs one-for-one without checking Nav3 docs.
- Assume deep links, nested graphs, or shared destinations work exactly like Navigation Compose.

---

## Legacy Compatibility

If you are modifying existing Navigation Compose code that has not migrated yet, preserve the local pattern unless the task explicitly includes migration. Do not mix `NavController` and Nav3 state management in the same flow without a clear migration plan.

---

## References

- [Navigation 3 overview](https://developer.android.com/guide/navigation/navigation-3)
- [Navigation 3 get started](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Navigation 3 basics](https://developer.android.com/guide/navigation/navigation-3/basics)
- [Navigation 3 save state](https://developer.android.com/guide/navigation/navigation-3/save-state)
- [Navigation 3 migration guide](https://developer.android.com/guide/navigation/navigation-3/migration-guide)
- [Navigation 3 recipes](https://github.com/android/nav3-recipes)
