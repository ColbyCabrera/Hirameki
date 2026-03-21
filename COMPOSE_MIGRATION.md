# AnkiDroid Compose, Nav3, and Single-Activity Migration Plan

Last updated: March 21, 2026

## Goal

Incrementally migrate AnkiDroid toward this target architecture:

```text
Single app shell activity
    -> Compose app/root host
    -> Nav3-backed destination model for Compose-only flows
    -> Route composables that collect state and perform UI-only behavior
    -> Screen ViewModels that handle business events and expose UiState
    -> Data/domain operations below the ViewModel
```

This document is intentionally opinionated. It is not a status scrapbook. It is a migration plan for getting from the current hybrid app to a single-activity, Compose-first, MVI-leaning architecture without a risky rewrite.

## External Guidance We Are Following

Android's current guidance is consistent on four points that matter here:

1. Compose favors unidirectional data flow: state flows down, events flow up.
2. Business logic belongs in the ViewModel or lower layers; UI behavior logic such as navigation calls, permission requests, and transient UI wiring remains in the UI layer.
3. Navigation Compose or Nav3 can only own a graph when all destinations in that graph are composables.
4. Navigation should pass stable identifiers, not complex objects.

Primary references:

- Compose architecture: https://developer.android.com/develop/ui/compose/architecture
- UI events and state guidance: https://developer.android.com/topic/architecture/ui-layer/events
- Navigation with Compose: https://developer.android.com/develop/ui/compose/navigation
- Migration from fragment navigation: https://developer.android.com/develop/ui/compose/migrate/migration-scenarios/navigation
- Navigation 3 overview: https://developer.android.com/guide/navigation/navigation-3

## Current Codebase Snapshot

The repository already contains real progress, but the app is still hybrid.

### What is already working

- `DeckPicker.kt` hosts Compose directly via `setContent`.
- `deckpicker/compose/DeckPickerNavHost.kt` already uses `NavDisplay` and typed Nav3 entries.
- `navigation/Navigator.kt` and `navigation/NavigationState.kt` provide a working Nav3 state model with per-stack state retention.
- Multiple major features already have Compose UI and ViewModel state holders:
    - DeckPicker
    - CardBrowser
    - Reviewer
    - NoteEditor
    - Drawing
    - Page/WebView-backed screens

### What is still preventing the target architecture

1. Major screens are still hosted by activities or fragments.
     - `NoteEditorActivity.kt` still hosts `NoteEditorFragment.kt`, which then hosts Compose.
     - `CardBrowser.kt` is still a standalone activity even though tablet mode already embeds browser content.
     - `Reviewer.kt` is Compose-hosted, but the activity still owns significant workflow and framework behavior.

2. Nav3 is currently an island, not the app shell.
     - `DeckPickerNavHost.kt` is real Nav3 usage.
     - But it still depends on activity callbacks such as `onLaunchIntent`, `onShowDialogFragment`, and other bridges back into `DeckPicker.kt`.

3. `AnkiActivity.kt` still centralizes a large amount of framework behavior.
     - Theme setup
     - dialog plumbing
     - export listeners
     - activity result launching
     - lifecycle helpers
     - fragment-result wiring

4. DialogFragment usage is still widespread.
     - `DeckPicker.kt`, `CardBrowser.kt`, preferences, export, confirmation flows, and several custom dialogs still depend on `supportFragmentManager`.

5. ViewModel contracts are not yet uniform.
     - Many screens already expose `StateFlow`.
     - Several still rely on `MutableSharedFlow` or `Channel` event buses for transient work.
     - Some business operations still live in activities instead of ViewModels/use cases.

## Architectural Rules For The Migration

These rules should guide all new work.

### 1. No new fragment wrappers for Compose screens

If a feature is already Compose-first, do not add a new `Fragment -> ComposeView` container around it.

### 2. New Compose screens should use Route + Screen separation

For each screen, prefer:

```text
Route composable
    - obtains ViewModel
    - collects UiState
    - collects effects if needed
    - performs navigation, permission, launcher, dialog bridge work

Screen composable
    - pure UI
    - receives immutable state and callbacks
```

This keeps navigation APIs and Android framework details out of reusable screen composables.

### 3. Business events go to the ViewModel

Examples:

- rebuilding a filtered deck
- refreshing data
- deleting content
- loading reviewer state

These should not stay in activities long term.

### 4. UI behavior stays in the UI layer

Examples:

- calling `navigator.navigate(...)`
- launching an activity result contract
- requesting permissions
- opening a file picker
- showing a platform dialog while legacy code still exists

These remain in route/app-shell code until the underlying framework dependency is abstracted away.

### 5. Pass IDs, not domain objects, through navigation

Nav3 destinations should carry small, stable route data such as deck IDs, card IDs, and mode flags. Destination data should be loaded from a single source of truth after navigation.

### 6. Prefer state over one-off ViewModel events for durable UI outcomes

Android guidance has become stricter here: navigation decisions and transient user messages should be modeled as UI state when they must survive recomposition, lifecycle restarts, or back stack retention.

During migration, limited effect streams are acceptable for bridging legacy platform work, but the end state should be:

- `UiState` for what the UI should represent
- intent handlers in the ViewModel for business events
- narrow effect APIs only where the UI must perform one-time framework work

## Target Architecture For This Repo

This is the intended steady state.

```text
AppActivity (single shell, likely evolving from AnkiActivity rather than replacing it immediately)
    -> setContent { AnkiApp() }
    -> AppNavigationState / Navigator / NavDisplay
    -> Route composables per destination
    -> Screen ViewModels per feature
    -> domain/data operations behind repositories/use cases/helpers
```

### MVI shape we should aim for

At the screen level, prefer this contract:

```kotlin
data class SomeUiState(...)

sealed interface SomeIntent
sealed interface SomeEffect

class SomeViewModel : ViewModel() {
        val uiState: StateFlow<SomeUiState>
        fun onIntent(intent: SomeIntent)
}
```

Guidance for using these types:

- `UiState`: everything the screen can render right now
- `Intent`: user or system input into the screen
- `Effect`: temporary bridge for UI-only work that cannot be modeled as durable state yet

Strict MVI is not the immediate prerequisite. Consistent UDF is.

## What Nav3 Should Mean In This Project

Nav3 is the right target, but only where its prerequisites are met.

### Use Nav3 when

- every destination in that flow is already a composable
- the flow benefits from explicit back stack ownership
- adaptive layouts need to show more than one destination at once

### Do not force Nav3 when

- a flow still depends on fragment destinations
- an activity still owns the lifecycle-critical behavior of the feature
- the feature still relies on `DialogFragment` or fragment results for essential work

### Practical implication

Keep using local Nav3 islands while the app is hybrid. Do not pretend the whole app has a single Nav3 graph until the major destinations in that graph are Compose-only.

## Feature Assessment

### DeckPicker

Current strengths:

- already Compose-hosted
- already using `NavDisplay`
- already has typed route objects and a custom navigation state model

Current blockers:

- `DeckPickerNavHost.kt` still depends on activity callbacks for launching intents and showing `DialogFragment`s
- `DeckPicker.kt` still owns startup, result handling, and several business-adjacent operations
- tablet mode still embeds other legacy constructs

Migration role:

- this is the current reference implementation for Nav3
- this should be the first feature converted from "Nav3 island inside an activity" to "route-first screen hosted by a real app shell"

### CardBrowser

Current strengths:

- mature Compose UI and ViewModel state
- already appears inside DeckPicker on tablets, which proves it can participate in a Compose-first flow

Current blockers:

- still has a standalone activity entry point
- still uses dialog fragments and activity-owned actions

Migration role:

- best candidate for the next major route extraction after DeckPicker stabilization

### Reviewer

Current strengths:

- Compose UI exists
- `ReviewerViewModel` already has recognizable state, event, and effect types

Current blockers:

- activity still owns substantial reviewer workflow
- `AbstractFlashcardViewer` and media/webview dependencies keep XML-era assumptions alive
- activity-level menu and launcher work is still significant

Migration role:

- likely the hardest high-value migration
- should move only after DeckPicker/CardBrowser and NoteEditor patterns are proven

### NoteEditor

Current strengths:

- Compose UI already exists
- ViewModel is already substantial

Current blockers:

- `NoteEditorActivity -> NoteEditorFragment -> ComposeView` layering remains
- toolbar, launcher, and fragment scoping still complicate the feature

Migration role:

- the best immediate win for removing a fragment wrapper without requiring app-wide navigation changes

## Incremental Migration Plan

### Phase 0: Stabilize The Rules

This phase is about preventing new debt while migration continues.

Required rules for new feature work:

1. New Compose screens must expose immutable state and callbacks.
2. New internal navigation code must use typed route data, not raw intent extras unless leaving the Compose island.
3. No new feature should add business logic to an activity if the ViewModel can own it.
4. New dialogs for Compose-first screens should be written in Compose unless blocked by a framework dependency.

### Phase 1: Standardize Screen Contracts

Introduce a repeatable pattern across Compose screens.

Deliverables:

1. Each major Compose feature has a route composable and a pure screen composable.
2. Each major screen ViewModel exposes a clearly named `UiState`.
3. New work prefers `onIntent(...)` or small verb handlers over activity callbacks.
4. Navigation callbacks are passed as lambdas rather than exposing navigator/controller objects deep in the tree.

Why this phase matters:

- It enables migration without needing the final app shell first.
- It reduces coupling before we move destinations into a shared Nav3 root.

### Phase 2: Remove Fragment Wrappers From Compose-First Features

Priority order:

1. NoteEditor
2. Remaining Compose-first dialog flows
3. Any other `Fragment -> ComposeView` containers used only as wrappers

Deliverables:

- `NoteEditorActivity` becomes a direct Compose host
- `NoteEditorFragment` goes away
- related XML container layouts become removable

This phase is intentionally local. It improves architecture without depending on a full single-activity rewrite.

### Phase 3: Move Business Workflow Out Of Activities

Priority features:

1. DeckPicker
2. CardBrowser
3. Reviewer

Deliverables:

- activity methods that perform collection, scheduler, or repository work are moved into ViewModels or lower layers
- activities keep only UI-shell responsibilities such as launchers, permission bridges, and top-level lifecycle wiring
- business workflows become testable without an activity host

Examples of the desired direction:

- rebuild or empty filtered deck logic should not stay in an activity
- loading reviewer state should not depend on activity-owned orchestration
- browser actions should stop routing through `supportFragmentManager` where a Compose dialog or route can own the interaction

### Phase 4: Replace DialogFragment Dependencies In Compose Flows

This is a major unlock for a real app shell.

Deliverables:

1. Compose dialogs/bottom sheets replace `DialogFragment` for Compose-first features.
2. Route composables own dialog visibility state.
3. `onShowDialogFragment` style callbacks disappear from Nav3 route APIs.

This phase should focus first on the most common dialogs in:

- DeckPicker
- CardBrowser
- NoteEditor

### Phase 5: Expand Nav3 From DeckPicker To App-Level Navigation

This phase begins only after a meaningful set of destinations are Compose-only.

Deliverables:

1. Create an app-level Compose root hosted by a single shell activity.
2. Move top-level navigation into a shared `NavDisplay` and typed destination model.
3. Keep local adaptive layouts where Nav3's explicit back stack ownership is beneficial.
4. Convert internal activity-to-activity navigation into route navigation for migrated features.

Important constraint:

Do not try to create a single Nav3 graph that still depends on fragment destinations. Android's migration guidance explicitly warns against mixed destination graphs.

### Phase 6: Converge On A Single Activity

This is the endgame, not the starting point.

Deliverables:

1. One app shell activity hosts the main app experience.
2. Former feature activities become composable destinations or external workflow launchers.
3. `AnkiActivity` is either reduced to reusable shell behavior or split into smaller platform services/helpers.
4. Intra-app navigation stops depending on intents for migrated features.

This phase may still keep separate activities for isolated platform-heavy flows if they remain simpler that way. "Single activity" is the goal for the main app experience, not a reason to force every platform entry point into one class prematurely.

## Recommended Near-Term Work

If we want the migration to move materially toward the target architecture, the highest-value sequence is:

1. Make NoteEditor direct-Compose and remove its fragment wrapper.
2. Standardize DeckPicker and CardBrowser around Route + Screen + ViewModel contracts.
3. Replace the most common DeckPicker and CardBrowser dialog fragments with Compose dialogs.
4. Move remaining business operations out of `DeckPicker.kt` and `CardBrowser.kt`.
5. Start an app-level Compose shell only after those feature seams are in place.

## Definition Of Done For The Architecture

We should consider the migration complete only when all the following are true:

1. The main app experience is hosted by one shell activity.
2. Internal screen-to-screen navigation for migrated features is route-based, not intent-based.
3. Compose-first features no longer depend on fragment wrappers.
4. Compose-first features no longer require `supportFragmentManager` for routine dialogs.
5. Screen business logic is testable in ViewModels or lower layers.
6. Nav3 owns the back stack for the Compose-only app shell.

## Anti-Goals

This migration should avoid these mistakes:

1. Big-bang rewrites.
2. Declaring Nav3 "done" while the app still depends on fragment destinations.
3. Moving every activity method into the ViewModel without separating business logic from UI behavior.
4. Passing full domain objects through navigation.
5. Building new Compose screens that still reach directly into activities for business actions.

## Tracking Notes

Keep this document focused on architecture and sequencing. Do not turn it back into a running changelog of every Compose cleanup or bug fix. Feature-specific migration details can live in feature-local documents when needed.
