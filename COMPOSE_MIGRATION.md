# AnkiDroid Compose & Nav3 Migration Status

**Last Updated**: December 31, 2025

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

### DeckPickerNavHost Extraction (Completed)
**Location**: `deckpicker/compose/DeckPickerNavHost.kt`

The navigation logic has been extracted from `DeckPicker.kt` into a dedicated `DeckPickerNavHost` composable:

| Change                 | Description                                                      |
|------------------------|------------------------------------------------------------------|
| `DeckPickerNavHost.kt` | New file (~770 lines) with all Nav3 navigation logic             |
| Nav3 `NavDisplay`      | Integrated with `DeckPickerScreen` and `HelpScreen` destinations |
| `DeckPickerWithDrawer` | Private composable handling drawer + main content                |
| `SetupFlows`           | Centralized Flow collectors for ViewModels                       |
| `LocalContext`         | Replaced `AnkiDroidApp.instance` with Compose-provided context   |

### Nav3 Destinations Active
```kotlin
@Serializable object DeckPickerScreen
@Serializable object HelpScreen
```

### Layout Fixes
- `statistics.xml`: Removed duplicate `fitsSystemWindows` causing edge-to-edge issues

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
- [ ] Clean up `NoteEditorFragment.kt` legacy code (~1950 lines)
- [ ] Test core functionality (add/edit notes)
- [ ] Tab order/accessibility
- [ ] CardBrowser split-view integration

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

### 8. Pages (WebView Screens) — 🔴 0% Compose
All use `PageFragment` with WebView wrapper. These render Anki desktop's HTML/JS content.

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
| 1        | StudyOptions | Separate Activity | Low    |
| 2        | Congrats     | Separate Activity | Low    |
| 3        | Statistics   | PageFragment      | Medium |
| 4        | DeckOptions  | PageFragment      | Medium |
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

---

## 📊 Effort Estimates (Updated)

| Phase                            | Effort | Status     |
|----------------------------------|--------|------------|
| Phase 1.1: DeckPicker Nav3       | Done   | ✅ Complete |
| Phase 1.2: StudyOptions/Congrats | Low    | ⬜ Next     |
| Phase 1.3: Statistics Nav3       | Medium | ⬜ Planned  |
| Phase 2: Complete Compose        | Large  | 🟡 Ongoing |
| Phase 3: Full Nav3               | Medium | ⬜ Future   |
