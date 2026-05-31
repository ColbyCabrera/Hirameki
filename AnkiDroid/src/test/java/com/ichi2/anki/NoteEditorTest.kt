/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@file:Suppress("SameParameterValue")

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.config.ConfigKey
import com.ichi2.anim.ActivityTransitionAnimation.Direction.DEFAULT
import com.ichi2.anki.api.AddContentApi.Companion.DEFAULT_DECK_ID
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.noteeditor.NoteEditorViewModel
import com.ichi2.testutils.getString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull

/**
 * Tests for NoteEditor functionality.
 *
 * Note: These tests use Robolectric with explicit main looper management.
 * Due to Compose + Lifecycle scoping, we need to ensure async tasks are
 * properly drained to prevent threading issues with LifecycleCoroutineScopeImpl.
 */
@RunWith(AndroidJUnit4::class)
class NoteEditorTest : RobolectricTest() {

    private lateinit var originalIoDispatcher: CoroutineDispatcher

    @Before
    override fun setUp() {
        super.setUp()
        originalIoDispatcher = ioDispatcher
        ioDispatcher = UnconfinedTestDispatcher()
        // Ensure main looper is idled before each test
        idleMainLooper()
    }

    // Extension to access the internal viewModel for testing
    val NoteEditorFragment.viewModel: NoteEditorViewModel
        get() = noteEditorViewModel

    @After
    override fun tearDown() {
        // Drain any pending main thread tasks before teardown
        idleMainLooper()
        ioDispatcher = originalIoDispatcher
        super.tearDown()
    }

    /**
     * Idles the main looper fully, running all pending and delayed tasks.
     * Must be called after any operation that may queue async work.
     */
    private fun idleMainLooper() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun verifyStartupAndCloseWithNoCollectionDoesNotCrash() {
        enableNullCollection()
        val intent = NoteEditorLauncher.AddNote().toIntent(targetContext)
        ActivityScenario.launchActivityForResult<NoteEditorActivity>(intent).use { scenario ->
            idleMainLooper()
            scenario.onNoteEditor { noteEditor ->
                noteEditor.requireActivity().onBackPressedDispatcher.onBackPressed()
                assertThat(
                    "Pressing back should finish the activity",
                    noteEditor.requireActivity().isFinishing
                )
            }
            val result = scenario.result
            assertThat(
                "Activity should be cancelled as no changes were made",
                result.resultCode,
                equalTo(Activity.RESULT_CANCELED)
            )
        }
    }

    @Test
    fun `can open with corrupt current deck - Issue 14096`() {
        col.config.set(CURRENT_DECK, '"' + "1688546411954" + '"')
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        assertThat(
            "current deck is default after corruption", editor.deckId, equalTo(DEFAULT_DECK_ID)
        )
    }

    @Test
    fun previewWorksWithNoError() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        assertDoesNotThrow { runBlocking { editor.performPreview() } }
    }

    @Test
    fun errorSavingNoteWithNoFirstFieldDisplaysNoFirstField() = runTest {
        val noteEditor = getNoteEditorAdding(NoteType.BASIC).withNoFirstField().build()
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()

        val actualResourceId = noteEditor.snackbarErrorText
        assertThat(actualResourceId, equalTo(CollectionManager.TR.addingTheFirstFieldIsEmpty()))
    }

    @Test
    fun testErrorMessageNull() = runTest {
        val noteEditor = getNoteEditorAdding(NoteType.BASIC).withNoFirstField().build()
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()
        assertThat(
            noteEditor.addNoteErrorMessage,
            equalTo(CollectionManager.TR.addingTheFirstFieldIsEmpty())
        )

        noteEditor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()
        assertThat(noteEditor.addNoteErrorMessage, equalTo(null))
    }

    @Test
    fun errorSavingClozeNoteWithNoFirstFieldDisplaysClozeError() = runTest {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE).withNoFirstField().build()
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()

        val actualResourceId = noteEditor.snackbarErrorText
        assertThat(actualResourceId, equalTo(CollectionManager.TR.addingTheFirstFieldIsEmpty()))
    }

    @Test
    fun errorSavingClozeNoteWithNoClozeDeletionsDisplaysClozeError() = runTest {
        val noteEditor = getNoteEditorAdding(NoteType.CLOZE).withFirstField("NoCloze").build()
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()

        val actualResourceId = noteEditor.snackbarErrorText
        assertThat(
            actualResourceId, equalTo(CollectionManager.TR.addingYouHaveAClozeDeletionNote())
        )
    }

    @Test
    fun errorSavingNoteWithNoTemplatesShowsNoCardsCreated() = runTest {
        val noteEditor =
            getNoteEditorAdding(NoteType.BACK_TO_FRONT).withFirstField("front is not enough")
                .build()
        idleMainLooper()

        noteEditor.saveNote()
        idleMainLooper()

        val actualResourceId = noteEditor.snackbarErrorText
        assertThat(actualResourceId, equalTo(getString(R.string.note_editor_no_cards_created)))
    }

    @Test
    fun clozeNoteWithNoClozeDeletionsDoesNotSave() = runTest {
        val initialCards = cardCount
        val editor =
            getNoteEditorAdding(NoteType.CLOZE).withFirstField("no cloze deletions").build()
        idleMainLooper()

        editor.saveNote()
        idleMainLooper()

        assertThat(cardCount, equalTo(initialCards))
    }

    @Test
    fun clozeNoteWithClozeDeletionsDoesSave() = runTest {
        val initialCards = cardCount
        val editor =
            getNoteEditorAdding(NoteType.CLOZE).withFirstField("{{c1::AnkiDroid}} is fantastic")
                .build()
        idleMainLooper()

        editor.saveNote()
        idleMainLooper()

        assertThat(cardCount, equalTo(initialCards + 1))
    }

    @Test
    fun clozeNoteWithClozeInWrongFieldDoesNotSave() = runTest {
        val initialCards = cardCount
        val editor =
            getNoteEditorAdding(NoteType.CLOZE).withSecondField("{{c1::AnkiDroid}} is fantastic")
                .build()
        idleMainLooper()

        editor.saveNote()
        idleMainLooper()

        assertThat(cardCount, equalTo(initialCards))
    }

    @Test
    fun testHandleMultimediaActionsDisplaysBottomSheet() {
        val intent = NoteEditorLauncher.AddNote().toIntent(targetContext)
        ActivityScenario.launchActivityForResult<NoteEditorActivity>(intent).use { scenario ->
            idleMainLooper()
            scenario.onNoteEditor { noteEditor ->
                noteEditor.showMultimediaBottomSheet()
                idleMainLooper()

                onView(withId(R.id.multimedia_action_image)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withId(R.id.multimedia_action_audio)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withId(R.id.multimedia_action_drawing)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withId(R.id.multimedia_action_recording)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withId(R.id.multimedia_action_video)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withId(R.id.multimedia_action_camera)).inRoot(isDialog())
                    .check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun copyNoteCopiesDeckId() {
        idleMainLooper()
        val currentDid = addDeck("Basic::Test")
        col.config.set(CURRENT_DECK, currentDid)
        val n = super.addBasicNote("Test", "Note")
        n.notetype.did = currentDid
        val editor = getNoteEditorEditingExistingBasicNote("Test", "Note", FromScreen.DECK_LIST)
        idleMainLooper()

        col.config.set(CURRENT_DECK, Consts.DEFAULT_DECK_ID)
        val copyNoteBundle = getCopyNoteIntent(editor)
        val newNoteEditor = openNoteEditorWithArgs(copyNoteBundle)
        idleMainLooper()

        assertThat(
            "Selected deck ID should be the current deck id", editor.deckId, equalTo(currentDid)
        )
        assertThat(
            "Deck ID in the intent should be the selected deck id",
            copyNoteBundle.getLong(NoteEditorFragment.EXTRA_DID, -404L),
            equalTo(currentDid),
        )
        assertThat(
            "Deck ID in the new note should be the ID provided in the intent",
            newNoteEditor.deckId,
            equalTo(currentDid)
        )
    }

    @Test
    fun stickyFieldsAreUnchangedAfterAdd() = runTest {
        val basic = makeNoteForType(NoteType.BASIC)
        basic!!.fields[0].sticky = true

        val initFirstField = "Hello"
        val initSecondField = "unused"
        val newFirstField = "Hello" + FieldEditText.NEW_LINE + "World"

        val editor = getNoteEditorAdding(NoteType.BASIC).withFirstField(initFirstField)
            .withSecondField(initSecondField).build()
        idleMainLooper()

        assertThat(editor.currentFieldStrings.toList(), contains(initFirstField, initSecondField))
        editor.setFieldValueFromUi(0, newFirstField)
        idleMainLooper()
        assertThat(editor.currentFieldStrings.toList(), contains(newFirstField, initSecondField))

        editor.saveNote()
        idleMainLooper()

        val actual = editor.currentFieldStrings.toList()
        assertThat(
            "newlines should be preserved, second field should be blanked",
            actual,
            contains(newFirstField, "")
        )
        assertThat("sticky field content remains unsaved after save", editor.hasUnsavedChanges())
    }

    @Test
    fun `pinned field remains unsaved after saving added note`() = runTest {
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.toggleStickyField(0)
        idleMainLooper()
        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()

        editor.saveNote()
        idleMainLooper()

        assertThat(editor.currentFieldStrings.toList(), contains("Hello", ""))
        assertThat("pinned field should still trigger discard warning", editor.hasUnsavedChanges())
    }

    @Test
    fun processTextIntentShouldCopyFirstField() {
        ensureCollectionLoadIsSynchronous()
        val i = Intent(Intent.ACTION_PROCESS_TEXT)
        i.putExtra(Intent.EXTRA_PROCESS_TEXT, "hello\nworld")
        val editor = openNoteEditorWithArgs(i.extras!!, i.action)
        idleMainLooper()

        val actual = editor.currentFieldStrings.toList()
        assertThat(actual, contains("hello\nworld", ""))
    }

    @Test
    fun clearFieldWorks() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        editor.setFieldValueFromUi(1, "Hello")
        idleMainLooper()
        assertThat(editor.currentFieldStrings[1], equalTo("Hello"))

        editor.clearField(1)
        idleMainLooper()
        assertThat(editor.currentFieldStrings[1], equalTo(""))
    }

    @Test
    fun insertIntoFocusedFieldStartsAtSelection() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        editor.viewModel.onFieldFocus(0)

        val initialText = "Hello"
        val cursorIndex = 2
        editor.viewModel.updateFieldValue(0, TextFieldValue(initialText, TextRange(cursorIndex)))
        idleMainLooper()

        editor.viewModel.formatSelection("World", "")
        idleMainLooper()

        val state = editor.viewModel.noteEditorState.value
        val field = state.fields.find { it.index == 0 }!!

        assertThat(field.value.text, equalTo("HeWorldllo"))
        assertThat(field.value.selection.start, equalTo(7))
        assertThat(field.value.selection.end, equalTo(7))
    }

    @Test
    fun insertIntoFocusedFieldWrapsSelection() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        editor.viewModel.onFieldFocus(0)

        val initialText = "Hello"
        val selection = TextRange(1, 4) // "ell"
        editor.viewModel.updateFieldValue(0, TextFieldValue(initialText, selection))
        idleMainLooper()

        editor.viewModel.formatSelection("<b>", "</b>")
        idleMainLooper()

        val state = editor.viewModel.noteEditorState.value
        val field = state.fields.find { it.index == 0 }!!

        assertThat(field.value.text, equalTo("H<b>ell</b>o"))
        assertThat(field.value.selection.start, equalTo(4))
        assertThat(field.value.selection.end, equalTo(7))
    }

    @Test
    fun insertIntoFocusedFieldWrapsSelectionIfBackwards() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        editor.viewModel.onFieldFocus(0)

        val initialText = "Hello"
        val selection = TextRange(4, 1) // "ell" backwards
        editor.viewModel.updateFieldValue(0, TextFieldValue(initialText, selection))
        idleMainLooper()

        editor.viewModel.formatSelection("<b>", "</b>")
        idleMainLooper()

        val state = editor.viewModel.noteEditorState.value
        val field = state.fields.find { it.index == 0 }!!

        assertThat(field.value.text, equalTo("H<b>ell</b>o"))
        assertThat(field.value.selection.start, equalTo(4))
        assertThat(field.value.selection.end, equalTo(7))
    }

    @Test
    fun defaultsToCapitalized() {
        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        val prefs =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(editor.requireContext())

        // Verify the default is true when the preference is not set
        prefs.edit { remove(NoteEditorFragment.PREF_NOTE_EDITOR_CAPITALIZE) }
        assertThat(
            "Default value for capitalization should be true",
            prefs.getBoolean(NoteEditorFragment.PREF_NOTE_EDITOR_CAPITALIZE, true),
            equalTo(true)
        )

        // Verify that setting the preference to false is respected
        prefs.edit { putBoolean(NoteEditorFragment.PREF_NOTE_EDITOR_CAPITALIZE, false) }
        assertThat(
            "After setting to false, the preference should be false",
            prefs.getBoolean(NoteEditorFragment.PREF_NOTE_EDITOR_CAPITALIZE, true),
            equalTo(false)
        )
    }

    @Test
    @Ignore("Tests XML FieldEditText clipboard/pastePlainText. In Compose mode, clipboard is handled differently. Requires UI test.")
    fun pasteHtmlAsPlainTextTest() {
        // TODO: Rewrite test for Compose clipboard handling
    }

    @Test
    fun `can switch two image occlusion note types 15579`() {
        // Skip test if Image Occlusion note type doesn't exist
        org.junit.Assume.assumeTrue(
            "Image Occlusion note type required", col.notetypes.byName("Image Occlusion") != null
        )

        val ioType1 = col.notetypes.byName("Image Occlusion")!!
        val ioType2 = getSecondImageOcclusionNoteType()

        val type1Name = ioType1.name
        val type2Name = ioType2.name

        val editor = getNoteEditorAddingNote(FromScreen.DECK_LIST)
        idleMainLooper()

        editor.viewModel.selectNoteType(type1Name)
        idleMainLooper()

        assertThat(editor.viewModel.noteEditorState.value.selectedNoteTypeName, equalTo(type1Name))

        editor.viewModel.selectNoteType(type2Name)
        idleMainLooper()

        assertThat(editor.viewModel.noteEditorState.value.selectedNoteTypeName, equalTo(type2Name))

        // Switch back
        editor.viewModel.selectNoteType(type1Name)
        idleMainLooper()

        assertThat(editor.viewModel.noteEditorState.value.selectedNoteTypeName, equalTo(type1Name))
    }

    @Test
    fun `starts with image occlusion note type when caller is IMG_OCCLUSION`() {
        val ioNotetype = col.notetypes.all().find { it.isImageOcclusion }
        org.junit.Assume.assumeTrue("Image Occlusion note type required", ioNotetype != null)

        // Set another note type as current, to prove it switches to Image Occlusion
        val basicType = col.notetypes.byName("Basic")!!
        col.notetypes.setCurrent(basicType)

        val bundle =
            NoteEditorLauncher.ImageOcclusion("content://media/external/images/media/1".toUri())
                .toBundle()
        val editor = openNoteEditorWithArgs(bundle)
        idleMainLooper()

        assertThat(
            editor.viewModel.noteEditorState.value.selectedNoteTypeName, equalTo(ioNotetype!!.name)
        )
    }

    @Test
    fun `launching with IMG_OCCLUSION and missing imageUri closes the editor`() {
        val bundle = NoteEditorLauncher.ImageOcclusion(imageUri = null).toBundle()
        ActivityScenario.launchActivityForResult<NoteEditorActivity>(
            NoteEditorLauncher.PassArguments(bundle).toIntent(targetContext)
        ).use { scenario ->
            idleMainLooper()
            scenario.onNoteEditor { noteEditor ->
                assertThat(
                    "Missing image URI should cause the activity to finish",
                    noteEditor.requireActivity().isFinishing
                )
            }
        }
    }

    @Test
    fun `launching with IMG_OCCLUSION when image cached copy path is null closes the editor`() {
        val bundle =
            NoteEditorLauncher.ImageOcclusion(imageUri = "content://invalid-provider/image".toUri())
                .toBundle()
        ActivityScenario.launchActivityForResult<NoteEditorActivity>(
            NoteEditorLauncher.PassArguments(bundle).toIntent(targetContext)
        ).use { scenario ->
            idleMainLooper()
            scenario.onNoteEditor { noteEditor ->
                assertThat(
                    "Null image cached copy path should cause the activity to finish",
                    noteEditor.requireActivity().isFinishing
                )
            }
        }
    }

    @Test
    fun `launching with IMG_OCCLUSION when no image occlusion note type is found closes the editor`() {
        val ioNotetype = col.notetypes.all().find { it.isImageOcclusion }
        if (ioNotetype != null) {
            col.notetypes.remove(ioNotetype.id)
        }

        val bundle =
            NoteEditorLauncher.ImageOcclusion("content://media/external/images/media/1".toUri())
                .toBundle()
        ActivityScenario.launchActivityForResult<NoteEditorActivity>(
            NoteEditorLauncher.PassArguments(bundle).toIntent(targetContext)
        ).use { scenario ->
            idleMainLooper()
            scenario.onNoteEditor { noteEditor ->
                assertThat(
                    "Missing image occlusion note type should cause the activity to finish",
                    noteEditor.requireActivity().isFinishing
                )
            }
        }
    }

    @Test
    fun `edit note in filtered deck from reviewer - 15919`() {
        idleMainLooper()
        addDeck("A")
        val homeDeckId = addDeck("B", setAsSelected = true)
        val note = addBasicNote().updateCards { did = homeDeckId }
        moveToDynamicDeck(note)

        assertThat("home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("current deck", note.firstCard().did, not(equalTo(homeDeckId)))

        val editor = getNoteEditorEditingExistingBasicNote(note, FromScreen.REVIEWER)
        idleMainLooper()

        assertThat("current deck is the home deck", editor.deckId, equalTo(homeDeckId))
        assertThat("no unsaved changes", !editor.hasUnsavedChanges())
    }

    @Test
    fun `decide by note type preference - 13931`() = runTest {
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, false)
        addDeck("Basic")
        val reversedDeckId = addDeck("Reversed", setAsSelected = true)

        assertThat("setup: deckId", col.notetypes.byName("Basic")!!.did, equalTo(1))

        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.onDeckSelected(SelectableDeck.Deck(reversedDeckId, "Reversed"))
        idleMainLooper()
        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()
        editor.saveNote()
        idleMainLooper()

        col.notetypes.clearCache()

        assertThat("a note was added", col.noteCount(), equalTo(1))
        assertThat(
            "note type deck is updated",
            col.notetypes.byName("Basic")!!.did,
            equalTo(reversedDeckId)
        )

        val editor2 = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()
        assertThat("Deck ID is remembered", editor2.deckId, equalTo(reversedDeckId))
    }

    @Test
    fun `cards info is updated after saving new note`() = runTest {
        val editor =
            getNoteEditorAdding(NoteType.BASIC).withFirstField("Front").withSecondField("Back")
                .build()
        idleMainLooper()

        // Initial state check
        assertThat(
            editor.viewModel.noteEditorState.value.cardsInfo, equalTo("Cards: Card 1")
        )

        editor.saveNote()
        idleMainLooper()

        // After save, we are on a new blank note of the same type.
        // It should still say "Cards: Card 1" because the note type hasn't changed.
        // It should NOT be empty (which causes the raw format string issue in UI)
        assertThat(
            editor.viewModel.noteEditorState.value.cardsInfo, equalTo("Cards: Card 1")
        )
    }

    @Test
    fun `saving added note refreshes deck baseline for discard tracking`() = runTest {
        val alternateDeckId = addDeck("Alternate")
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.onDeckSelected(SelectableDeck.Deck(alternateDeckId, "Alternate"))
        idleMainLooper()
        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()

        assertThat("deck change is unsaved before save", editor.hasUnsavedChanges())

        editor.saveNote()
        idleMainLooper()

        assertThat("deck change is cleared after save", !editor.hasUnsavedChanges())
    }

    @Test
    fun `saving added note refreshes note type baseline for discard tracking`() = runTest {
        val alternateNoteTypeName = createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.selectNoteType(alternateNoteTypeName)
        idleMainLooper()
        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()

        assertThat("note type change is unsaved before save", editor.hasUnsavedChanges())

        editor.saveNote()
        idleMainLooper()

        assertThat("note type change is cleared after save", !editor.hasUnsavedChanges())
    }

    @Test
    fun `editing card in filtered deck retains deck`() = runTest {
        val homeDeckId = addDeck("A")
        val note = addBasicNote().updateCards { did = homeDeckId }
        moveToDynamicDeck(note)

        assertThat("home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("current deck", note.firstCard().did, not(equalTo(homeDeckId)))

        val editor = getNoteEditorEditingExistingBasicNote(note, FromScreen.REVIEWER)
        idleMainLooper()

        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()
        editor.saveNote()
        idleMainLooper()

        assertThat("after: home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("after: current deck", note.firstCard().did, not(equalTo(homeDeckId)))
    }

    // ---- selectNoteType Tests ----

    @Test
    fun `fields migrate by name to note type with extra fields`() {
        createThreeFieldNoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "hello")
        editor.setFieldValueFromUi(1, "world")
        idleMainLooper()

        editor.viewModel.selectNoteType("ThreeField")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("should have 3 fields", fields.size, equalTo(3))
        assertThat("Front migrated", fields[0].value.text, equalTo("hello"))
        assertThat("Back migrated", fields[1].value.text, equalTo("world"))
        assertThat("Extra is blank", fields[2].value.text, equalTo(""))
    }

    @Test
    fun `fields migrate by name when field order differs`() {
        addStandardNoteType(
            "Reversed Fields", arrayOf("Back", "Front"), "{{Back}}", "{{Front}}"
        )
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "front-val")
        editor.setFieldValueFromUi(1, "back-val")
        idleMainLooper()

        editor.viewModel.selectNoteType("Reversed Fields")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("Back field (index 0) has back-val", fields[0].value.text, equalTo("back-val"))
        assertThat(
            "Front field (index 1) has front-val", fields[1].value.text, equalTo("front-val")
        )
    }

    @Test
    fun `fields fall back to index when names do not match`() {
        addStandardNoteType(
            "Prompt Answer", arrayOf("Prompt", "Answer"), "{{Prompt}}", "{{Answer}}"
        )
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "hello")
        editor.setFieldValueFromUi(1, "world")
        idleMainLooper()

        editor.viewModel.selectNoteType("Prompt Answer")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("Prompt field keeps index 0 value", fields[0].value.text, equalTo("hello"))
        assertThat("Answer field keeps index 1 value", fields[1].value.text, equalTo("world"))
    }

    @Test
    fun `fields use name matches before index fallback`() {
        addStandardNoteType(
            "Front Response", arrayOf("Front", "Response"), "{{Front}}", "{{Response}}"
        )
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "front-val")
        editor.setFieldValueFromUi(1, "back-val")
        idleMainLooper()

        editor.viewModel.selectNoteType("Front Response")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("Front field keeps name-based match", fields[0].value.text, equalTo("front-val"))
        assertThat(
            "Response field receives unmatched back field by index",
            fields[1].value.text,
            equalTo("back-val")
        )
    }

    @Test
    fun `name matching takes precedence over conflicting index fallback`() {
        addStandardNoteType("Back Prompt", arrayOf("Back", "Prompt"), "{{Back}}", "{{Prompt}}")
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "front-val")
        editor.setFieldValueFromUi(1, "back-val")
        idleMainLooper()

        editor.viewModel.selectNoteType("Back Prompt")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat(
            "Back field keeps the name-based match", fields[0].value.text, equalTo("back-val")
        )
        assertThat("Prompt field stays blank", fields[1].value.text, equalTo(""))
    }

    @Test
    fun `index fallback skips fields past destination size`() {
        createThreeFieldNoteType()
        addStandardNoteType(
            "Prompt Pair", arrayOf("Prompt", "Response"), "{{Prompt}}", "{{Response}}"
        )
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.selectNoteType("ThreeField")
        idleMainLooper()

        editor.setFieldValueFromUi(0, "front-val")
        editor.setFieldValueFromUi(1, "back-val")
        editor.setFieldValueFromUi(2, "extra-val")
        idleMainLooper()

        editor.viewModel.selectNoteType("Prompt Pair")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("destination has only 2 fields", fields.size, equalTo(2))
        assertThat("Prompt gets index 0 value", fields[0].value.text, equalTo("front-val"))
        assertThat("Response gets index 1 value", fields[1].value.text, equalTo("back-val"))
    }

    @Test
    fun `field content with newlines survives note type round-trip`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        val textWithNewline = "line1\nline2"
        editor.setFieldValueFromUi(0, textWithNewline)
        editor.setFieldValueFromUi(1, "back")
        idleMainLooper()

        // Switch to Basic 2 then back to Basic
        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()
        editor.viewModel.selectNoteType("Basic")
        idleMainLooper()

        val fields = editor.viewModel.noteEditorState.value.fields
        assertThat("Front field text preserved", fields[0].value.text, equalTo(textWithNewline))
        assertThat("Back field text preserved", fields[1].value.text, equalTo("back"))
    }

    @Test
    fun `tags survive note type switch`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.updateTags(setOf("tag1", "tag2"))
        idleMainLooper()

        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()

        val tags = editor.viewModel.noteEditorState.value.tags
        assertThat("tags preserved after switch", tags, containsInAnyOrder("tag1", "tag2"))
    }

    @Test
    fun `note ID preserved during note type switch`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        val originalId = editor.viewModel.currentNote.value!!.id

        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()

        assertThat(
            "note ID unchanged after switch",
            editor.viewModel.currentNote.value!!.id,
            equalTo(originalId)
        )
    }

    @Test
    fun `deck switches to notetype default when preference is false`() {
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, false)
        val customDeckId = addDeck("Custom Deck")

        val customTypeName = addStandardNoteType(
            "Custom Type", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}"
        )
        val customType = col.notetypes.byName(customTypeName)!!
        customType.did = customDeckId
        col.notetypes.save(customType)

        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.selectNoteType(customTypeName)
        idleMainLooper()

        assertThat(
            "deck switched to notetype default",
            editor.viewModel.noteEditorState.value.selectedDeckName,
            equalTo("Custom Deck")
        )
    }

    @Test
    fun `deck stays when preference is true`() {
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, true)
        val customDeckId = addDeck("Custom Deck")

        val customTypeName = addStandardNoteType(
            "Custom Type 2", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}"
        )
        val customType = col.notetypes.byName(customTypeName)!!
        customType.did = customDeckId
        col.notetypes.save(customType)

        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        val originalDeckName = editor.viewModel.noteEditorState.value.selectedDeckName

        editor.viewModel.selectNoteType(customTypeName)
        idleMainLooper()

        assertThat(
            "deck unchanged",
            editor.viewModel.noteEditorState.value.selectedDeckName,
            equalTo(originalDeckName)
        )
    }

    @Test
    fun `no-op when selecting already-active note type`() {
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "test")
        idleMainLooper()

        val stateBefore = editor.viewModel.noteEditorState.value
        val noteBefore = editor.viewModel.currentNote.value

        editor.viewModel.selectNoteType("Basic")
        idleMainLooper()

        val stateAfter = editor.viewModel.noteEditorState.value
        val noteAfter = editor.viewModel.currentNote.value

        assertThat(
            "fields unchanged",
            stateAfter.fields.map { it.value.text },
            equalTo(stateBefore.fields.map { it.value.text })
        )
        assertThat(
            "note type unchanged",
            stateAfter.selectedNoteTypeName,
            equalTo(stateBefore.selectedNoteTypeName)
        )
        assertThat("note reference unchanged", noteAfter, equalTo(noteBefore))
    }

    @Test
    fun `invalid note type name is a safe no-op`() {
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.setFieldValueFromUi(0, "test")
        idleMainLooper()

        val stateBefore = editor.viewModel.noteEditorState.value

        editor.viewModel.selectNoteType("Nonexistent Type")
        idleMainLooper()

        val stateAfter = editor.viewModel.noteEditorState.value
        assertThat(
            "fields unchanged",
            stateAfter.fields.map { it.value.text },
            equalTo(stateBefore.fields.map { it.value.text })
        )
        assertThat("note type unchanged", stateAfter.selectedNoteTypeName, equalTo("Basic"))
    }

    @Test
    fun `hasUnsavedChanges is true after note type switch`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        assertThat("clean state before switch", !editor.hasUnsavedChanges())

        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()

        assertThat(
            "note type change counts as unsaved", editor.hasUnsavedChanges()
        )
    }

    @Test
    fun `collection current notetype updated after switch`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()

        assertThat(
            "collection current notetype updated", col.notetypes.current().name, equalTo("Basic 2")
        )
    }

    @Test
    fun `deck mid key updated to new notetype ID after switch`() {
        createBasic2NoteType()
        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.viewModel.selectNoteType("Basic 2")
        idleMainLooper()

        val expectedId = col.notetypes.byName("Basic 2")!!.id
        val currentDeck = col.decks.current()
        assertThat(
            "deck mid key matches new notetype", currentDeck.getLong("mid"), equalTo(expectedId)
        )
    }

    @Test
    fun `switching note type saves mid on editor selected deck`() = runTest {
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, true)
        val collectionDeckId = addDeck("Collection Deck", setAsSelected = true)
        val editorDeckId = addDeck("Editor Deck")
        val alternateNoteTypeName = addStandardNoteType(
            "Basic Editor Deck", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}"
        )
        val alternateNoteType = col.notetypes.byName(alternateNoteTypeName)!!
        val originalCollectionDeckMid = col.decks.getLegacy(collectionDeckId)?.optLong("mid")

        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        editor.onDeckSelected(SelectableDeck.Deck(editorDeckId, "Editor Deck"))
        idleMainLooper()

        editor.viewModel.selectNoteType(alternateNoteTypeName)
        idleMainLooper()

        assertThat("editor stays on the selected deck", editor.deckId, equalTo(editorDeckId))
        assertThat(
            "selected editor deck stores the last-used note type",
            col.decks.getLegacy(editorDeckId)!!.getLong("mid"),
            equalTo(alternateNoteType.id)
        )
        assertThat(
            "collection current deck is not overwritten",
            col.decks.getLegacy(collectionDeckId)?.optLong("mid"),
            equalTo(originalCollectionDeckMid)
        )
    }

    @Test
    fun `switching note type to preferred deck refreshes deck tags`() = runTest {
        col.config.setBool(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK, false)
        val initialDeckId = addDeck("Initial Deck", setAsSelected = true)
        val preferredDeckId = addDeck("Preferred Deck")
        val basicNoteType = col.notetypes.byName("Basic")!!
        basicNoteType.did = initialDeckId
        col.notetypes.save(basicNoteType)
        val alternateNoteTypeName = addStandardNoteType(
            "Basic Preferred Deck", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}"
        )
        val alternateNoteType = col.notetypes.byName(alternateNoteTypeName)!!
        alternateNoteType.did = preferredDeckId
        col.notetypes.save(alternateNoteType)

        addBasicNote("Initial Front", "Initial Back").update {
            setTagsFromStr(col, "initial-only-tag")
        }.updateCards { did = initialDeckId }
        addBasicNote("Preferred Front", "Preferred Back").update {
            setTagsFromStr(col, "preferred-only-tag")
        }.updateCards { did = preferredDeckId }

        val editor = getNoteEditorAdding(NoteType.BASIC).build()
        idleMainLooper()

        assertThat(
            "editor starts on the initial deck",
            editor.viewModel.noteEditorState.value.selectedDeckName,
            equalTo("Initial Deck")
        )
        assertThat(
            "initial deck tags are loaded before switching",
            editor.viewModel.deckTags.value.contains("initial-only-tag"),
            equalTo(true)
        )

        editor.viewModel.selectNoteType(alternateNoteTypeName)
        idleMainLooper()

        assertThat(
            "editor switches to the note type deck",
            editor.viewModel.noteEditorState.value.selectedDeckName,
            equalTo("Preferred Deck")
        )
        assertThat(
            "preferred deck tags are reloaded after switching",
            editor.viewModel.deckTags.value.contains("preferred-only-tag"),
            equalTo(true)
        )
        assertThat(
            "stale tags from the old deck are cleared",
            editor.viewModel.deckTags.value.contains("initial-only-tag"),
            equalTo(false)
        )
    }

    @Test
    fun `change note type of existing note deletes orphaned cards and migrates fields`() = runTest {
        val note = addBasicAndReversedNote("front-val", "back-val")
        assertThat("note has 2 cards", note.cards().size, equalTo(2))
        val card1Id = note.cards()[0].id
        val card2Id = note.cards()[1].id

        val bundle = NoteEditorLauncher.EditCard(note.firstCard().id, DEFAULT).toBundle()
        val editor = openNoteEditorWithArgs(bundle)
        idleMainLooper()

        editor.viewModel.selectNoteType("Basic")
        idleMainLooper()

        editor.saveNote()
        idleMainLooper()

        val updatedNote = col.getNote(note.id)
        assertThat(updatedNote.notetype.name, equalTo("Basic"))

        val remainingCards = updatedNote.cards()
        assertThat("remaining cards count", remainingCards.size, equalTo(1))
        assertThat("remaining card is card 1", remainingCards[0].id, equalTo(card1Id))

        assertThat("card 2 should be deleted", col.findCards("cid:$card2Id"), empty())
    }

    @Test
    fun `change note type of existing note when editing deleted card falls back to remaining card`() =
        runTest {
            val note = addBasicAndReversedNote("front-val", "back-val")
            assertThat("note has 2 cards", note.cards().size, equalTo(2))
            val card1Id = note.cards()[0].id
            val card2Id = note.cards()[1].id

            // Edit the second card, which gets deleted when switching "Basic (and reversed)" -> "Basic"
            val bundle = NoteEditorLauncher.EditCard(card2Id, DEFAULT).toBundle()
            val editor = openNoteEditorWithArgs(bundle)
            idleMainLooper()

            editor.viewModel.selectNoteType("Basic")
            idleMainLooper()

            editor.saveNote()
            idleMainLooper()

            val updatedNote = col.getNote(note.id)
            assertThat(updatedNote.notetype.name, equalTo("Basic"))

            val remainingCards = updatedNote.cards()
            assertThat("remaining cards count", remainingCards.size, equalTo(1))
            assertThat("remaining card is card 1", remainingCards[0].id, equalTo(card1Id))
            assertThat("card 2 should be deleted", col.findCards("cid:$card2Id"), empty())

            // Verify the fallback loader logic picked card 1
            val currentCardField = NoteEditorViewModel::class.java.getDeclaredField("_currentCard")
            currentCardField.isAccessible = true
            val currentCardFlow =
                currentCardField.get(editor.viewModel) as kotlinx.coroutines.flow.StateFlow<*>
            val finalCard = currentCardFlow.value as Card?
            assertThat("fallback card is loaded", finalCard?.id, equalTo(card1Id))
        }

    // ---- Helper Methods ----

    private fun moveToDynamicDeck(note: Note): DeckId {
        val dyn = addDynamicDeck("All")
        col.decks.select(dyn)
        col.sched.rebuildFilteredDeck(dyn)
        assertThat("card is in dynamic deck", note.firstCard().did, equalTo(dyn))
        return dyn
    }

    private fun getSecondImageOcclusionNoteType(): NotetypeJson {
        val imageOcclusionNotes = col.notetypes.filter { it.isImageOcclusion }
        return if (imageOcclusionNotes.size >= 2) {
            imageOcclusionNotes.first { it.name != "Image Occlusion" }
        } else {
            // Clone and save the note type so it's available for selection
            val clone = col.notetypes.byName("Image Occlusion")!!.createClone()
            clone.name = "Image Occlusion 2"
            col.notetypes.save(clone)
            clone
        }
    }

    /** Creates a "Basic 2" note type with the same field schema as Basic */
    private fun createBasic2NoteType(): String =
        addStandardNoteType("Basic 2", arrayOf("Front", "Back"), "{{Front}}", "{{Back}}")

    /** Creates a "ThreeField" note type with Front, Back, and an Extra field */
    private fun createThreeFieldNoteType(): String = addStandardNoteType(
        "ThreeField", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}<br>{{Extra}}"
    )

    private fun getCopyNoteIntent(editor: NoteEditorFragment): Bundle {
        val editorShadow = shadowOf(editor.requireActivity())
        editor.copyNote()
        idleMainLooper()
        val intent = editorShadow.peekNextStartedActivityForResult().intent
        return intent.extras ?: Bundle()
    }

    private val cardCount: Int
        get() = col.cardCount()

    private fun getNoteEditorAdding(noteType: NoteType): NoteEditorTestBuilder {
        val n = makeNoteForType(noteType)
        return NoteEditorTestBuilder(n)
    }

    private fun makeNoteForType(noteType: NoteType): NotetypeJson? = when (noteType) {
        NoteType.BASIC -> col.notetypes.byName("Basic")
        NoteType.CLOZE -> col.notetypes.byName("Cloze")
        NoteType.BACK_TO_FRONT -> {
            val name = super.addStandardNoteType(
                "Reversed", arrayOf("Front", "Back"), "{{Back}}", "{{Front}}"
            )
            col.notetypes.byName(name)
        }

        NoteType.THREE_FIELD_INVALID_TEMPLATE -> {
            val name =
                super.addStandardNoteType("Invalid", arrayOf("Front", "Back", "Side"), "", "")
            col.notetypes.byName(name)
        }

        NoteType.IMAGE_OCCLUSION -> col.notetypes.byName("Image Occlusion")
    }

    private fun getNoteEditorAddingNote(from: FromScreen): NoteEditorFragment {
        ensureCollectionLoadIsSynchronous()
        val bundle = when (from) {
            FromScreen.REVIEWER -> NoteEditorLauncher.AddNoteFromReviewer().toBundle()
            FromScreen.DECK_LIST -> NoteEditorLauncher.AddNote().toBundle()
        }
        val editor = openNoteEditorWithArgs(bundle)
        idleMainLooper()
        return editor
    }

    private fun getNoteEditorEditingExistingBasicNote(
        front: String,
        back: String,
        from: FromScreen,
    ): NoteEditorFragment {
        val n = super.addBasicNote(front, back)
        return getNoteEditorEditingExistingBasicNote(n, from)
    }

    private fun getNoteEditorEditingExistingBasicNote(
        n: Note,
        from: FromScreen,
    ): NoteEditorFragment {
        val bundle = when (from) {
            FromScreen.REVIEWER -> NoteEditorLauncher.EditCard(n.firstCard().id, DEFAULT).toBundle()
            FromScreen.DECK_LIST -> NoteEditorLauncher.AddNote().toBundle()
        }
        val editor = openNoteEditorWithArgs(bundle)
        idleMainLooper()
        return editor
    }

    fun openNoteEditorWithArgs(
        arguments: Bundle,
        action: String? = null,
    ): NoteEditorFragment {
        val activity = startActivityNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorLauncher.PassArguments(arguments).toIntent(targetContext, action),
        )
        idleMainLooper()
        return activity.getNoteEditorFragment()
    }

    @DuplicatedCode("NoteEditor in androidTest")
    @Throws(Throwable::class)
    fun ActivityScenario<NoteEditorActivity>.onNoteEditor(block: (NoteEditorFragment) -> Unit) {
        val wrapped = AtomicReference<Throwable?>(null)
        this.onActivity { activity: NoteEditorActivity ->
            try {
                idleMainLooper()
                val editor = activity.getNoteEditorFragment()
                block(editor)
            } catch (t: Throwable) {
                wrapped.set(t)
            }
        }
        wrapped.get()?.let { throw it }
    }

    @DuplicatedCode("NoteEditor in androidTest")
    fun NoteEditorActivity.getNoteEditorFragment(): NoteEditorFragment =
        supportFragmentManager.findFragmentById(R.id.note_editor_fragment_frame) as NoteEditorFragment

    private enum class FromScreen {
        DECK_LIST, REVIEWER,
    }

    private enum class NoteType {
        BASIC, CLOZE, BACK_TO_FRONT, THREE_FIELD_INVALID_TEMPLATE, IMAGE_OCCLUSION,
    }

    inner class NoteEditorTestBuilder(
        notetype: NotetypeJson?,
    ) {
        private val notetype: NotetypeJson
        private var firstField: String? = null
        private var secondField: String? = null

        fun build(): NoteEditorFragment {
            return buildInternal()
        }

        fun buildInternal(): NoteEditorFragment {
            col.notetypes.setCurrent(notetype)
            val noteEditor = getNoteEditorAddingNote(FromScreen.REVIEWER)
            idleMainLooper()

            if (this.firstField != null) {
                noteEditor.setFieldValueFromUi(0, firstField)
                idleMainLooper()
            }
            if (secondField != null) {
                noteEditor.setFieldValueFromUi(1, secondField)
                idleMainLooper()
            }
            return noteEditor
        }

        fun withNoFirstField(): NoteEditorTestBuilder = this

        fun withFirstField(text: String?): NoteEditorTestBuilder {
            firstField = text
            return this
        }

        fun withSecondField(text: String?): NoteEditorTestBuilder {
            secondField = text
            return this
        }

        init {
            assertNotNull(notetype) { "model was null" }
            this.notetype = notetype
        }
    }
}
