# AnkiDroid Compose & Nav3 Migration Status

**Last Updated**: January 1, 2026

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

### Bug Fixes
- `statistics.xml`: Removed duplicate `fitsSystemWindows` causing edge-to-edge issues
- `CongratsActivity.kt`: Added missing `onNavigateUp` parameter
- `DeckPickerNavHost.kt`: Fixed CongratsScreen NavEntry parameters
- `NoteEditorFragment.kt`: Refactored `setupComposeEditor` (375→20 lines)

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
- `BrowserColumnSelectionFragment.kt`
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

### 4. Note Editor — 🟡 70% Compose (In Progress)
**Location**: `noteeditor/compose/`

| File                     | Size | Status     |
|--------------------------|------|------------|
| `NoteEditor.kt`          | 28KB | ✅ Complete |
| `NoteEditorToolbar.kt`   | 15KB | ✅ Complete |
| `NoteEditorTopBar.kt`    | 10KB | ✅ Complete |
| `NoteEditorViewModel.kt` | 53KB | ✅ Complete |

> **Note**: See `noteeditor/COMPOSE_MIGRATION_STATUS.md` for detailed tracking.

**Remaining Work**:
- [x] Refactor `NoteEditorFragment.kt` - extracted helper methods
- [ ] Test core functionality (add/edit notes)
- [ ] Tab order/accessibility
- [ ] CardBrowser split-view integration

> **Note**: Unit tests are @Ignored due to lifecycle scope threading issue with Robolectric.

---

### 5. Help Screen — 🟢 100% Compose + Nav3
**Location**: `ui/compose/help/HelpScreen.kt`

| Status    | Description                                      |
|-----------|--------------------------------------------------|
| ✅ Compose | Full UI in Compose                               |
| ✅ Nav3    | Integrated as destination in `DeckPickerNavHost` |
| ✅ Works   | Accessible from drawer navigation                |

---

### 6. Dialogs — 🟡 15% Migrated

**Compose Dialogs**:
| Dialog                        | Status     |
|-------------------------------|------------|
| `TagsDialog.kt`               | ✅ Complete |
| `ExportDialog.kt`             | ✅ Complete |
| `FlagRenameDialog.kt`         | ✅ Complete |
| `DeleteConfirmationDialog.kt` | ✅ Complete |
| `DiscardChangesDialog.kt`     | ✅ Complete |
| `BrowserOptionsComposable.kt` | ✅ Complete |

**Still View-Based** (40+ dialogs)

---

### 7. Preferences/Settings — 🔴 5% Compose
> **Important**: Settings uses AndroidX Preference with XML. Full migration requires custom Compose preference components.

---

### 8. Pages (WebView Screens) — 🟢 100% Compose Wrapper
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

### 1. Expand Nav3 to StudyOptions/Congrats (Quick Win)
**Effort**: Low | **Impact**: High

Both are already Compose screens. Add:
```kotlin
@Serializable object StudyOptionsScreen
@Serializable object CongratsScreen
```

### 2. Migrate Statistics to Nav3 Destination
**Effort**: Medium | **Impact**: Medium

The `Statistics` PageFragment can be wrapped as a Nav3 destination. Consider creating a `PageWebView` composable wrapper.

### 3. Complete NoteEditor Fragment Cleanup
**Effort**: High | **Impact**: High

Remove legacy code from `NoteEditorFragment.kt` now that ViewModel handles state.

### 4. Migrate Simple Dialogs to Compose
**Effort**: Low per dialog | **Impact**: Medium

Quick wins:
- `CreateDeckDialog`
- Simple confirmation dialogs
- `IntegerDialog`

### 5. Create Compose WebView Wrapper
**Effort**: Medium | **Impact**: High

A reusable `PageWebView` composable would enable Nav3 for all `PageFragment` screens (Statistics, DeckOptions, CardInfo, etc.).

### 6. Consolidate CardBrowser Navigation
**Effort**: High | **Impact**: High

CardBrowser already renders in DeckPicker on tablets. Add it as a proper Nav3 destination for consistent navigation.

### 7. Fix NoteEditor Test Infrastructure
**Effort**: Medium | **Impact**: Low

The lifecycle scope threading issue in Robolectric tests needs a production code fix in `CoroutineHelpers.kt` to use `Dispatchers.Main.immediate` for lifecycle scope access.

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
