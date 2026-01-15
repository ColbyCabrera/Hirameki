# AnkiDroid Compose & Nav3 Migration Status

**Last Updated**: January 7, 2026

---

## Executive Summary

| Metric                         | Status                 |
|--------------------------------|------------------------|
| **Activities**                 | 20 (0% Compose-only)   |
| **Fragments**                  | 57+ (few migrated)     |
| **Compose Screen Files**       | 12                     |
| **Files with @Composable**     | 55+                    |
| **XML Layouts**                | 150+                   |
| **Estimated Compose Adoption** | ~40-45% of UI          |
| **Nav3 Integration**           | ✅ Started (DeckPicker) |

---

## 🎯 Architectural Goal: Single Activity, No Fragments

Modern Android best practices recommend **single-activity architecture** with Compose navigation.

### Current State
```
Activity → Fragment → ComposeView → Screen Composable
```

### Target State (Nav3 + Compose)
```
Single Activity → NavDisplay → Screen Composables
```

### Benefits
- **Simpler navigation** - Nav3 handles backstack, deep links, animations
- **Less boilerplate** - No Fragment lifecycle, no FragmentManager
- **Better testability** - Pure composables are easier to test
- **Type-safe navigation** - Serializable destination objects

### Migration Path
1. **Current**: Fragments host ComposeViews (hybrid)
2. **Next**: Make screens Nav3 destinations, keep Fragment as thin wrapper
3. **Final**: Eliminate Fragments, screens are pure composables in NavDisplay

---

## 🆕 Recent Progress

### PageWebView Compose Wrapper (Completed)
**Location**: `pages/`

Created reusable Compose wrapper for displaying Anki HTML pages via WebView:

| File                      | Description                         |
|---------------------------|-------------------------------------|
| `PageWebViewViewModel.kt` | Manages AnkiServer lifecycle        |
| `PageWebView.kt`          | Composable with AndroidView wrapper |
| `StatisticsScreen.kt`     | Graphs page wrapper                 |
| `DeckOptionsScreen.kt`    | Deck options wrapper                |
| `CardInfoScreen.kt`       | Card info wrapper                   |

### Nav3 Destinations Active
```kotlin
@Serializable object DeckPickerScreen
@Serializable object HelpScreen
@Serializable object StudyOptionsScreen
@Serializable object CongratsScreen
@Serializable object StatisticsDestination
@Serializable data class DeckOptionsDestination(val deckId: Long)
@Serializable data class CardInfoDestination(val cardId: Long)
```

### Bug Fixes & Code Quality
- `statistics.xml`: Removed duplicate `fitsSystemWindows` causing edge-to-edge issues
- `CongratsActivity.kt`: Added missing `onNavigateUp` parameter
- `DeckPickerNavHost.kt`: Fixed CongratsScreen NavEntry parameters, added error handling for `withCol` operations
- `NoteEditorFragment.kt`: Refactored `setupComposeEditor` (375→20 lines), simplified card info extraction
- `NoteEditorViewModel.kt`: Fixed threading issues, consolidated duplicate `ToolbarDialogState` class
- `NoteEditorTest.kt`: Resolved all test failures, added proper dispatcher restoration in tearDown
- `PageWebView.kt`: Fixed stale error UI bug when loading new URLs
- `PageWebViewClient.kt`: Added defensive try-catch to callback loops
- `DeckPickerViewModel.kt`: Extracted `calculateTimeUntilNextDay` helper function
- `NoteEditorDialogs.kt`: Added input validation and trimming for toolbar customization
- `PageWebViewViewModel.kt`: Converted `ServerState` to sealed interface
- `DeckPicker.kt`: Added user-visible error feedback for failed drag-and-drop imports

### Legacy Reviewer XML Cleanup (January 3, 2026)
Deleted legacy reviewer XML layouts that are now dead code since `Reviewer.kt` uses `ComposeView`:

**Deleted Files:**
- `reviewer2.xml`
- `reviewer_fullscreen.xml`
- `reviewer_fullscreen_noanswers.xml`
- `reviewer_topbar.xml`
- `reviewer_flashcard.xml`
- `reviewer_flashcard_fullscreen.xml`
- `reviewer_flashcard_fullscreen_noanswers.xml`
- `reviewer_mic_tool_bar.xml`

### DrawingActivity Compose Migration (January 3, 2026)
Migrated the drawing screen to Jetpack Compose.

**New Components:**
- `DrawingActivity.kt`: Migrated to use `setContent` with `DrawingScreen`
- `DrawingScreen.kt`: New proper Compose screen
- `DrawingViewModel.kt`: ViewModel for drawing logic, undo, and saving

**Deleted Legacy Files:**
- `activity_drawing.xml`
- `reviewer_whiteboard_editor.xml`
- `Whiteboard.kt` (Legacy View implementation)
- `reviewer/compose/Whiteboard.kt` (Unused wrapper)

**Code Changes:**
- `Reviewer.kt`: Removed dead `getContentViewAttr()` override
- `LayoutValidationTest.kt`: Removed `reviewer2` from ignored layouts
- `02-strings.xml`: Removed duplicate `whiteboard_stroke_width` string

**Files Kept (still in use):**
- `reviewer_menu_*.xml` - Used by Reviewer Menu Settings preferences
- `reviewer.xml` - Minimal stub (see Technical Debt below)

### CreateDeckDialog Compose Migration (January 7, 2026)
Migrated the deck creation dialog to Jetpack Compose with ViewModel-based state management.

**New Components:**
- `dialogs/compose/CreateDeckDialog.kt` - Compose dialog supporting DECK, SUB_DECK, RENAME_DECK, FILTERED_DECK types
- `DeckPickerViewModel.kt` - Added `CreateDeckDialogState`, `validateDeckName()`, `createDeck()`, and show/dismiss functions
- `dialogs/compose/CreateDeckDialogTest.kt` - Unit tests for helper functions

**Architecture Pattern:**
- ViewModel exposes `createDeckDialogState: StateFlow<CreateDeckDialogState>` (sealed class: Hidden/Visible)
- Validation returns enum (`DeckNameError.INVALID_NAME`, `ALREADY_EXISTS`) instead of strings to avoid Context dependency in ViewModel
- Composable maps enum to localized strings via `stringResource()`
- `DeckSelectionDialog.kt` and `DeckSpinnerSelection.kt` updated with callback properties for gradual migration
- Fallback to legacy dialog when callbacks not set (backwards compatible)

**Files Modified:**
- `dialogs/CreateDeckDialog.kt` - Marked `@Deprecated` with ReplaceWith hint
- `dialogs/DeckSelectionDialog.kt` - Added `onShowCreateDeckDialog`, `onShowCreateSubDeckDialog` callbacks with fallback
- `DeckSpinnerSelection.kt` - Added matching callbacks, wired to dialog
- `DeckPicker.kt` - Added `@file:Suppress("DEPRECATION")` for legacy usage
- `dialogs/CreateDeckDialogTest.kt` - Added `@Suppress("DEPRECATION")`

**Remaining Work:**
- Wire up callbacks in call sites to use Compose dialog (DeckPicker, NoteEditor, CardBrowser)
- Remove legacy `CreateDeckDialog.kt` once all call sites migrated

### Compose Popup Leak Fix Pattern
Fixed memory leaks in `DropdownMenu` components by ensuring menus are dismissed **before** executing action callbacks:

```kotlin
// ❌ WRONG - causes PopupLayout memory leak
DropdownMenuItem(onClick = {
    onAction()                    // Action first
    expanded = false              // Dismiss after
})

// ✅ CORRECT - prevents leak
DropdownMenuItem(onClick = {
    expanded = false              // Dismiss first
    onAction()                    // Action after
})
```

**Files Fixed:**
- `AnkiDroidApp.kt` (StudyOptionsScreen menu items)
- `ReviewerTopBar.kt` (flag selection)
- `WhiteboardToolbar.kt` (overflow menu)
- `ManageNoteTypesComposable.kt` (note type menu)
- `NoteEditor.kt` (type and deck selectors)
- `DeckSelector.kt` (deck selection)

---

## 🔧 Technical Debt

### CreateDeckDialog Call Site Migration
**Issue**: `CreateDeckDialog.kt` (legacy) has been deprecated in favor of `com.ichi2.anki.dialogs.compose.CreateDeckDialog`. 

**Architecture**: The Compose dialog uses a callback-based integration pattern:
1. `DeckSelectionDialog` and `DeckSpinnerSelection` expose optional callbacks (`onShowCreateDeckDialog`, `onShowCreateSubDeckDialog`)
2. Parent activities wire callbacks to their ViewModel
3. Fallback to legacy dialog when callbacks are null (backwards compatible)

**TODO**: Wire up callbacks in remaining call sites:
- `DeckPicker.kt` - `showCreateFilteredDeckDialog()` (uses legacy directly)
- `NoteEditorFragment.kt` - via `DeckSpinnerSelection`
- `CardBrowser.kt` - via `DeckSpinnerSelection`
- `CardTemplateEditor.kt`
- Call sites using `DeckSelectionDialog` without setting callbacks

### CreateDeckDialog ViewModel Duplication
**Issue**: `CardBrowserViewModel` and `DeckPickerViewModel` both contain nearly identical code for managing the CreateDeckDialog state, validation, and creation logic.

**Current State**: This duplication was introduced during the Compose migration to enable both ViewModels to independently manage their dialog state. The implementations are similar but have context-specific post-creation behaviors.

**Future Consideration**: Once the pattern stabilizes across all call sites, consider extracting shared logic into a helper class (NOT a base ViewModel, which creates tight coupling). However, this is low priority because:
1. The duplication is intentional for now to allow independent evolution
2. Each ViewModel may need unique post-creation behavior
3. Premature abstraction adds complexity without proven benefit

### AbstractFlashcardViewer Layout Dependency
**Issue**: `AbstractFlashcardViewer.onCreate()` calls `setContentView(getContentViewAttr())` which requires a valid XML layout. While `Reviewer.kt` immediately overrides this with `ComposeView`, the base class still needs the layout to exist.

**Current Workaround**: `reviewer.xml` is maintained as a minimal stub containing only the essential view IDs (`root_layout`, `flashcard`, `touch_layer`, `chosen_answer`, `mic_tool_bar_layer`, `top_bar`) needed by `AbstractFlashcardViewer`.

**Future Fix**: Refactor `AbstractFlashcardViewer` to not require XML layouts, or split it so only subclasses that need XML extend a different base class.

### DrawingActivity Not Yet Migrated
**Issue**: `DrawingActivity.kt` still uses `activity_drawing.xml` which includes `reviewer_whiteboard_editor.xml`. This is a standalone activity for creating drawings to add to notes.

**Future Fix**: Migrate `DrawingActivity` to Compose, which would allow deleting `reviewer_whiteboard_editor.xml` and the associated style `reviewer_whiteboard_editor_button_style`.

### FileProvider Authority Magic String
**Issue**: The string `".apkgfileprovider"` is duplicated across 8+ files for constructing FileProvider authorities:
- `AndroidManifest.xml`
- `AnkiActivity.kt`
- `DrawingViewModel.kt`
- `IntentHandler.kt`
- `MultimediaFragment.kt`
- `MultimediaImageFragment.kt`
- `SharedDecksDownloadFragment.kt`

**Future Fix**: Create a centralized constant (e.g., `FileProviderUtil.AUTHORITY_SUFFIX`) and update all usages to prevent maintenance issues and typos.

### PageWebViewViewModel AndroidViewModel Usage
**Issue**: `PageWebViewViewModel` extends `AndroidViewModel`, holding an `Application` context. This couples the ViewModel to the Android framework and reduces testability (can't use standard JUnit tests without Robolectric/AndroidX Test).

**Current State**: The ViewModel needs `Application` context to manage `AnkiServer` lifecycle. This is functional but not ideal for unit testing.

**Future Fix**: Refactor to use constructor injection via a factory. Create `PageWebViewViewModelFactory` that injects a `ServerProvider` interface, allowing test doubles to be used in unit tests.

### Generic Exception Catching Audit
**Issue**: ~220 usages of `catch (e: Exception)` or `catch (e: Throwable)` were found in the codebase. In coroutine contexts, this can accidentally swallow `CancellationException`, causing structured concurrency issues.

**Current State**: These require manual triage on a case-by-case basis. Some are intentional (framework callbacks), others may need to rethrow `CancellationException`.

**Future Fix**: Audit each usage incrementally. For coroutine contexts, ensure either:
1. `CancellationException` is rethrown: `catch (e: Exception) { if (e is CancellationException) throw e; ... }`
2. Or use `runCatching` with explicit handling
3. Or catch specific exception types instead of generic `Exception`

---

## ✅ Compose Adoption by Feature

### 1. Deck Picker (DeckPicker.kt) — 🟢 95% Compose
**Location**: `deckpicker/compose/`

| File                     | Size | Status     |
|--------------------------|------|------------|
| `DeckPickerNavHost.kt`   | 33KB | ✅ NEW      |
| `DeckPickerScreen.kt`    | 26KB | ✅ Complete |
| `DeckItem.kt`            | 13KB | ✅ Complete |
| `StudyOptionsScreen.kt`  | 18KB | ✅ Complete |
| `NoDecks.kt`             | 11KB | ✅ Complete |
| `SyncProgressDialog.kt`  | 3KB  | ✅ Complete |
| `DeckPickerViewModel.kt` | 20KB | ✅ Complete |

**Navigation Integration**:
- ✅ Nav3 `NavDisplay` with `DeckPickerScreen` and `HelpScreen`
- ✅ Navigator class with type-safe backstack
- ✅ Drawer + NavigationRail for tablet layout
- ✅ CardBrowser embedded on tablets (fragmented mode)

**Still View-Based**:
- `DeckPicker.kt` Activity container (hybrid - hosts Compose via `setContent`)

---

### 2. Card Browser — 🟢 85% Compose
**Location**: `browser/compose/`

| File                      | Size | Status     |
|---------------------------|------|------------|
| `CardBrowserScreen.kt`    | 45KB | ✅ Complete |
| `CardBrowserLayout.kt`    | 24KB | ✅ Complete |
| `FilterByTagsDialog.kt`   | 3KB  | ✅ Complete |
| `CardBrowserViewModel.kt` | 62KB | ✅ Complete |

**Still View-Based**:
- `CardBrowser.kt` Activity container
- `BrowserColumnSelectionFragment.kt` - Uses `fitsSystemWindows` workaround for edge-to-edge status bar overlap
- `ColumnSelectionDialogFragment.kt`
- `RepositionCardFragment.kt`
- `FindAndReplaceDialogFragment.kt`

---

### 3. Reviewer — 🟢 80% Compose
**Location**: `reviewer/compose/`

| File                         | Size | Status     |
|------------------------------|------|------------|
| `ReviewerCompose.kt`         | 30KB | ✅ Complete |
| `ReviewerTopBar.kt`          | 9KB  | ✅ Complete |
| `AnswerButtons.kt`           | 6KB  | ✅ Complete |
| `Flashcard.kt`               | 6KB  | ✅ Complete |
| `WhiteboardToolbar.kt`       | 11KB | ✅ Complete |
| `WhiteboardOptionsDialog.kt` | 12KB | ✅ Complete |
| `ColorPickerDialog.kt`       | 8KB  | ✅ Complete |
| `Whiteboard.kt`              | 1KB  | ✅ Complete |
| `WhiteboardCanvas.kt`        | 3KB  | ✅ Complete |
| `ReviewerViewModel.kt`       | 20KB | ✅ Complete |

**Still View-Based**:
- `Reviewer.kt` - Activity host (1373 lines, hybrid)
- `AbstractFlashcardViewer.kt` - Base class with WebView logic
- Audio recording toolbar

---

### 4. Note Editor — 🟢 75% Compose
**Location**: `noteeditor/compose/`

| File                     | Size | Status     |
|--------------------------|------|------------|
| `NoteEditor.kt`          | 28KB | ✅ Complete |
| `NoteEditorToolbar.kt`   | 15KB | ✅ Complete |
| `NoteEditorTopBar.kt`    | 10KB | ✅ Complete |
| `NoteEditorDialogs.kt`   | 7KB  | ✅ Complete |
| `NoteEditorViewModel.kt` | 57KB | ✅ Complete |

> **Note**: See `noteeditor/COMPOSE_MIGRATION_STATUS.md` for detailed tracking.

**Recent Work**:
- [x] Refactor `NoteEditorFragment.kt` - extracted helper methods
- [x] Fix unit test threading issues (injectable `ioDispatcher`)
- [x] Consolidate duplicate state classes
- [x] Add input validation to toolbar customization dialog
- [ ] Tab order/accessibility
- [ ] CardBrowser split-view integration


> **Note**: Unit tests are now passing after resolving lifecycle scope threading issues.

### 5. Note Editor Test Migration
**Location**: `src/test/java/com/ichi2/anki/NoteEditorTest.kt`

The following tests are `@Ignore`d and require rewriting for Compose APIs:

| Scope          | Test Name                                                   | Issue & Goal                                                                                          |
|----------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **Clipboard**  | `pasteHtmlAsPlainTextTest` (L389)                           | **Issue**: Tests XML `FieldEditText` behavior.<br>**Goal**: Verify Compose clipboard semantics.       |

---

### 6. Help Screen — 🟢 100% Compose + Nav3
**Location**: `ui/compose/help/HelpScreen.kt`

| Status    | Description                                      |
|-----------|--------------------------------------------------|
| ✅ Compose | Full UI in Compose                               |
| ✅ Nav3    | Integrated as destination in `DeckPickerNavHost` |
| ✅ Works   | Accessible from drawer navigation                |

---

### 7. Dialogs — 🟡 22% Migrated

**Compose Dialogs**:
| Dialog                        | Status                      |
|-------------------------------|-----------------------------|
| `TagsDialog.kt`               | ✅ Complete                  |
| `ExportDialog.kt`             | ✅ Complete                  |
| `FlagRenameDialog.kt`         | ✅ Complete                  |
| `DeleteConfirmationDialog.kt` | ✅ Complete                  |
| `DiscardChangesDialog.kt`     | ✅ Complete                  |
| `BrowserOptionsComposable.kt` | ✅ Complete                  |
| `NoteEditorDialogs.kt`        | ✅ Complete                  |
| `OnErrorCallback.kt`          | ✅ Complete                  |
| `CreateDeckDialog.kt`         | ✅ Complete (Legacy deprecated, call sites pending) |

**Still View-Based** (34+ dialogs)

---

### 8. Preferences/Settings — 🔴 5% Compose
> **Important**: Settings uses AndroidX Preference with XML. Full migration requires custom Compose preference components.

---

### 9. Pages (WebView Screens) — 🟢 100% Compose Wrapper
Created `PageWebView` composable wrapper for all Anki HTML/JS content:
- `StatisticsScreen.kt` - Nav3 destination
- `DeckOptionsScreen.kt` - Nav3 destination
- `CardInfoScreen.kt` - Nav3 destination

---

## 📋 Nav3 Migration Status

### Current State
| Component                      | Status                                    |
|--------------------------------|-------------------------------------------|
| Nav3 Dependencies              | ✅ Added                                   |
| `Navigator` class              | ✅ Created (`navigation/AppNavigation.kt`) |
| `NavDisplay`                   | ✅ Integrated in `DeckPickerNavHost`       |
| `DeckPickerScreen` destination | ✅ Working                                 |
| `HelpScreen` destination       | ✅ Working                                 |

### Next Nav3 Destinations to Add
| Priority | Screen       | Current           | Effort |
|----------|--------------|-------------------|--------|
| 1        | StudyOptions | ✅ NavEntry        | Done   |
| 2        | Congrats     | ✅ NavEntry        | Done   |
| 3        | Statistics   | ✅ NavEntry        | Done   |
| 4        | DeckOptions  | ✅ NavEntry        | Done   |
| 5        | CardBrowser  | Separate Activity | High   |
| 6        | Reviewer     | Separate Activity | High   |

---

## ⚡ Recommended Next Steps (Priority Order)

### 1. Complete NoteEditor Fragment Cleanup
**Effort**: High | **Impact**: High

Remove legacy code from `NoteEditorFragment.kt` now that the ViewModel handles state. This is a crucial step to fully modernize the Note Editor.

### 2. Consolidate CardBrowser Navigation
**Effort**: High | **Impact**: High

The CardBrowser already renders inside the DeckPicker on tablets. To create a consistent user experience, it should be migrated to a proper Nav3 destination, removing its dependency on a separate Activity.

### 3. Migrate Simple Dialogs to Compose
**Effort**: Low per dialog | **Impact**: Medium

Migrating the remaining 34+ view-based dialogs to Compose is a good source of small, incremental tasks. Quick wins include:
- Simple confirmation dialogs
- `IntegerDialog`

### 4. Code Quality TODOs

| File                   | Issue                                           | Suggested Fix                                                                                   |
|------------------------|-------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `DeckPickerNavHost.kt` | 24 parameters on `DeckPickerNavHost()` function | Group related callbacks into configuration objects (e.g., `DeckActions`, `NavigationCallbacks`) |

---

## 📊 Effort Estimates (Updated)

| Phase                            | Effort | Status     |
|----------------------------------|--------|------------|
| Phase 1.1: DeckPicker Nav3       | Done   | ✅ Complete |
| Phase 1.2: StudyOptions/Congrats | Done   | ✅ Complete |
| Phase 1.3: Statistics Nav3       | Done   | ✅ Complete |
| Phase 1.4: DeckOptions Nav3      | Done   | ✅ Complete |
| Phase 2: Complete Compose        | Large  | 🟡 Ongoing |
| Phase 3: Full Nav3               | Medium | ⬜ Future   |
