# AnkiDroid Compose & Nav3 Migration Status

**Last Updated**: December 31, 2025

---

## Executive Summary

| Metric                         | Status               |
|--------------------------------|----------------------|
| **Activities**                 | 20 (0% Compose-only) |
| **Fragments**                  | 57+ (few migrated)   |
| **Compose Screen Files**       | 11                   |
| **Files with @Composable**     | 51+                  |
| **XML Layouts**                | 150+                 |
| **Estimated Compose Adoption** | ~35-40% of UI        |

---

## ✅ Compose Adoption by Feature

### 1. Deck Picker (DeckPicker.kt) — 🟢 90% Compose
**Location**: `deckpicker/compose/`

| File                     | Size | Status     |
|--------------------------|------|------------|
| `DeckPickerScreen.kt`    | 26KB | ✅ Complete |
| `DeckItem.kt`            | 13KB | ✅ Complete |
| `StudyOptionsScreen.kt`  | 18KB | ✅ Complete |
| `NoDecks.kt`             | 11KB | ✅ Complete |
| `SyncProgressDialog.kt`  | 3KB  | ✅ Complete |
| `DeckPickerViewModel.kt` | 20KB | ✅ Complete |

**Still View-Based**:
- `DeckPicker.kt` Activity container (hybrid - hosts Compose)
- Navigation drawer (`NavigationDrawerActivity.kt`)

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

### 5. Compose Dialogs — 🟡 15% Migrated
**Location**: `dialogs/compose/`

| Compose Dialog                | Status     |
|-------------------------------|------------|
| `TagsDialog.kt`               | ✅ Complete |
| `ExportDialog.kt`             | ✅ Complete |
| `FlagRenameDialog.kt`         | ✅ Complete |
| `DeleteConfirmationDialog.kt` | ✅ Complete |
| `DiscardChangesDialog.kt`     | ✅ Complete |
| `BrowserOptionsComposable.kt` | ✅ Complete |

**Still View-Based** (40+ dialogs):
- `DatabaseErrorDialog.kt` (35KB - complex)
- `SyncErrorDialog.kt` (16KB)
- `DeckSelectionDialog.kt` (18KB)
- `CreateDeckDialog.kt` (13KB)
- All CustomStudy dialogs
- All Tag dialogs (except TagsDialog)
- Import/Export dialogs
- TTS dialogs

---

### 6. Preferences/Settings — 🔴 5% Compose
**Location**: `preferences/`

| Component                    | Status    |
|------------------------------|-----------|
| `AboutScreen.kt`             | ✅ Compose |
| `SliderPreferenceContent.kt` | ✅ Compose |

**Still Fragment/XML** (21 fragments):
- `HeaderFragment.kt` - Main settings screen
- `GeneralSettingsFragment.kt`
- `ReviewingSettingsFragment.kt`
- `AppearanceSettingsFragment.kt`
- `ControlsSettingsFragment.kt`
- `AccessibilitySettingsFragment.kt`
- `AdvancedSettingsFragment.kt`
- `SyncSettingsFragment.kt`
- `NotificationsSettingsFragment.kt`
- All other settings fragments

> **Important**: Preferences uses AndroidX Preference library with XML. Full migration requires custom Compose preference components.

---

### 7. Pages (WebView Screens) — 🔴 0% Compose
**Location**: `pages/`

All use `PageFragment` with WebView wrapper:
- `Statistics.kt` / `StatisticsDestination.kt`
- `DeckOptions.kt` 
- `CongratsPage.kt`
- `ImageOcclusion.kt`
- `CardInfoDestination.kt`
- `CsvImporter.kt`

> **Note**: These render Anki desktop's HTML/JS content. Migration would require rewriting in Compose or using Compose WebView wrapper.

---

### 8. Multimedia — 🔴 0% Compose
**Location**: `multimedia/`

All View-based:
- `MultimediaActivity.kt`
- `MultimediaFragment.kt`
- `MultimediaImageFragment.kt` (31KB - largest)
- `AudioVideoFragment.kt`
- `AudioRecordingFragment.kt`

---

### 9. Other Compose Components

**Shared Components** (`ui/compose/components/`):
| Component | Purpose |
|-----------|---------|
| `ExpandableFab.kt` | Floating action button with expansion |
| `SyncIcon.kt` | Animated sync icon |
| `MorphingCardCount.kt` | Animated deck counts |
| `LoadingIndicator.kt` | Loading spinner |
| `LoginErrorCard.kt` | Error state card |
| `Scrim.kt` | Overlay background |
| `CheckboxPrompt.kt` | Checkbox with label |
| `RoundedPolygonShape.kt` | Custom shapes |

**Other Compose Screens**:
| Screen | Location |
|--------|----------|
| `CongratsScreen.kt` | Study completion |
| `MyAccountScreen.kt` | Account management |
| `HelpScreen.kt` | Help/support |
| `Introduction.kt` | App intro |

---

## 🔴 Components NOT Yet Migrated

### Activities (20 total)
| Activity                         | Complexity | Dependencies                             |
|----------------------------------|------------|------------------------------------------|
| `DeckPicker.kt`                  | High       | Hosts Compose, nav drawer, many dialogs  |
| `CardBrowser.kt`                 | High       | Hosts Compose, many dialogs              |
| `Reviewer.kt`                    | High       | Hosts Compose, WhiteboardFragment, audio |
| `NoteEditorActivity.kt`          | Medium     | Hosts NoteEditorFragment                 |
| `MultimediaActivity.kt`          | Medium     | Fragment-based, camera/file intents      |
| `PreferencesActivity.kt`         | Medium     | Hosts 21 preference fragments            |
| `CardViewerActivity.kt`          | Low        | Single fragment host                     |
| `LoginActivity.kt`               | Low        | OAuth flow                               |
| `IntroductionActivity.kt`        | Low        | Compose intro screens                    |
| `SharedDecksActivity.kt`         | Medium     | WebView-based                            |
| `DrawingActivity.kt`             | Medium     | Canvas drawing                           |
| `InstantNoteEditorActivity.kt`   | Medium     | Quick add widget                         |
| `HelpActivity.kt`                | Low        | Simple                                   |
| `PermissionsActivity.kt`         | Low        | Permissions flow                         |
| `StudyOptionsComposeActivity.kt` | Low        | Compose host                             |
| `CongratsActivity.kt`            | Low        | Compose host                             |
| `ManageSpaceActivity.kt`         | Low        | Single fragment                          |

### Fragments (57+ total)
Key fragments requiring migration:
- All 21 Settings Fragments
- `PreviewerFragment.kt` / `TemplatePreviewerFragment.kt`
- `MediaCheckFragment.kt`
- All Multimedia fragments (5)
- `EmptyCardsDialogFragment.kt`
- `PageFragment.kt` and derivatives

---

## 📋 Nav3 Migration Plan

### Phase 1: Consolidate to Single Activity (Pre-Nav3)
**Goal**: Reduce 20 activities to ~3-5 activity entry points

#### Step 1.1: Define Activity Boundaries
Keep separate Activities for:
1. **MainActivity** - Main app (DeckPicker, Browser, Reviewer, Editor)
2. **PreferencesActivity** - Settings (until Compose preferences ready)
3. **AuthActivity** - Login/OAuth flows
4. **ExternalEntryActivity** - Intent handling (imports, shortcuts)

#### Step 1.2: Convert Activities to Compose Destinations
Priority order:

| Priority | Screen        | Current                       | Target              | Effort |
|----------|---------------|-------------------------------|---------------------|--------|
| 1        | StudyOptions  | `StudyOptionsComposeActivity` | Compose destination | Low    |
| 2        | Congrats      | `CongratsActivity`            | Compose destination | Low    |
| 3        | Help          | `HelpActivity`                | Compose destination | Low    |
| 4        | NoteEditor    | `NoteEditorActivity`          | Compose destination | Medium |
| 5        | Multimedia    | `MultimediaActivity`          | Compose destination | High   |
| 6        | SharedDecks   | `SharedDecksActivity`         | Compose destination | Medium |
| 7        | CardPreviewer | `CardViewerActivity`          | Compose destination | Medium |
| 8        | Drawing       | `DrawingActivity`             | Compose destination | Medium |

#### Step 1.3: Implement Navigation (Nav2 Stepping Stone)
Use Nav2 with type-safe routes as temporary solution:
```kotlin
// Define sealed class routes
sealed class AppRoute {
    object DeckPicker : AppRoute()
    object CardBrowser : AppRoute()
    data class Reviewer(val deckId: Long) : AppRoute()
    data class NoteEditor(val noteId: Long?) : AppRoute()
    // ...
}
```

---

### Phase 2: Complete Compose Migration
**Goal**: Reach 90%+ Compose UI coverage

#### Step 2.1: Migrate Remaining Dialogs
| Dialog                      | Effort | Priority |
|-----------------------------|--------|----------|
| Simple confirmation dialogs | Low    | High     |
| `CreateDeckDialog`          | Medium | High     |
| `DeckSelectionDialog`       | Medium | High     |
| `SyncErrorDialog`           | Medium | Medium   |
| `DatabaseErrorDialog`       | High   | Low      |
| CustomStudy dialogs         | Medium | Medium   |

#### Step 2.2: Migrate Multimedia
| Component           | Effort | Notes              |
|---------------------|--------|--------------------|
| Image picker/camera | High   | Platform APIs      |
| Audio recording     | High   | MediaRecorder      |
| Video playback      | Medium | ExoPlayer          |
| Drawing canvas      | Medium | Compose Canvas API |

#### Step 2.3: Migrate Preferences
| Approach                   | Effort | Notes                          |
|----------------------------|--------|--------------------------------|
| Compose preference library | Medium | Use accompanist-pref or custom |
| Custom Compose screens     | High   | Full control, more work        |

---

### Phase 3: Adopt Navigation 3
**Goal**: Replace Nav2/startActivity with Nav3

#### Step 3.1: Add Dependencies
```kotlin
implementation("androidx.navigation3:navigation3-runtime:1.0.0")
implementation("androidx.navigation3:navigation3-ui:1.0.0")
```

#### Step 3.2: Create NavKey Definitions
```kotlin
@Serializable
sealed class Screen {
    @Serializable object DeckPicker : Screen()
    @Serializable object CardBrowser : Screen()
    @Serializable data class Reviewer(val deckId: Long) : Screen()
    @Serializable data class NoteEditor(val noteId: Long?) : Screen()
    @Serializable data class DeckOptions(val deckId: Long) : Screen()
    @Serializable data class Statistics(val deckId: Long?) : Screen()
}
```

#### Step 3.3: Create NavDisplay in MainActivity
```kotlin
@Composable
fun MainNavHost() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.DeckPicker) }
    
    NavDisplay(
        backStack = backStack,
        entryProvider = { key ->
            when (key) {
                is Screen.DeckPicker -> entry(key) { DeckPickerScreen(...) }
                is Screen.CardBrowser -> entry(key) { CardBrowserScreen(...) }
                is Screen.Reviewer -> entry(key) { ReviewerScreen(key.deckId, ...) }
                // ...
            }
        }
    )
}
```

#### Step 3.4: Implement Adaptive Layouts (Scenes API)
For tablet/foldable support:
```kotlin
NavDisplay(
    backStack = backStack,
    scenes = listOf(
        ListDetailScene { /* list-detail layout */ }
    )
)
```

---

## 📊 Effort Estimates

| Phase                      | Effort         | Timeline       |
|----------------------------|----------------|----------------|
| Phase 1 (Consolidate)      | Large          | 2-3 months     |
| Phase 2 (Complete Compose) | Large          | 3-4 months     |
| Phase 3 (Nav3)             | Medium         | 1-2 months     |
| **Total**                  | **Very Large** | **6-9 months** |

---

## ⚡ Quick Wins (Low Effort, High Value)

1. **Merge StudyOptionsComposeActivity into DeckPicker** - Already Compose
2. **Merge CongratsActivity into DeckPicker flow** - Already Compose
3. **Finish NoteEditor cleanup** - Most work done
4. **Migrate simple dialogs** - ConfirmationDialog, IntegerDialog, etc.

---

## 📝 Next Steps (Recommended Order)

1. [ ] Complete NoteEditor testing and cleanup
2. [ ] Merge StudyOptions/Congrats into DeckPicker navigation
3. [ ] Migrate remaining simple dialogs to Compose
4. [ ] Create Compose multimedia components
5. [ ] Evaluate Compose preferences library
6. [ ] Consolidate to single MainActivity with Nav2
7. [ ] Once >80% Compose, adopt Nav3
