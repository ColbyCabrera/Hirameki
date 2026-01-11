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
import android.widget.Spinner
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.config.ConfigKey
import com.ichi2.anim.ActivityTransitionAnimation.Direction.DEFAULT
import com.ichi2.anki.NoteEditorTest.FromScreen.DECK_LIST
import com.ichi2.anki.NoteEditorTest.FromScreen.REVIEWER
import com.ichi2.anki.api.AddContentApi.Companion.DEFAULT_DECK_ID
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.CURRENT_DECK
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.noteeditor.NoteEditorViewModel
import com.ichi2.anki.noteeditor.compose.NoteEditorState
import com.ichi2.testutils.getString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
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

    // Access to NoteEditorViewModel for testing
    val NoteEditorFragment.viewModel: NoteEditorViewModel
        get() {
            // "noteEditorViewModel" is delegated, so the backing field is "noteEditorViewModel$delegate"
            val field = NoteEditorFragment::class.java.getDeclaredField("noteEditorViewModel\$delegate")
            field.isAccessible = true
            val lazyValue = field.get(this) as Lazy<*>
            return lazyValue.value as NoteEditorViewModel
        }

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
        val editor = getNoteEditorAddingNote(DECK_LIST)
        assertThat(
            "current deck is default after corruption", editor.deckId, equalTo(DEFAULT_DECK_ID)
        )
    }

    @Test
    fun previewWorksWithNoError() {
        val editor = getNoteEditorAddingNote(DECK_LIST)
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
        val editor = getNoteEditorEditingExistingBasicNote("Test", "Note", DECK_LIST)
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
        val editor = getNoteEditorAddingNote(DECK_LIST)
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
        val editor = getNoteEditorAddingNote(DECK_LIST)
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
        val editor = getNoteEditorAddingNote(DECK_LIST)
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
        val editor = getNoteEditorAddingNote(DECK_LIST)
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
        val editor = getNoteEditorAddingNote(DECK_LIST)
        idleMainLooper()

        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(editor.requireContext())
        assertThat(prefs.getBoolean("note_editor_capitalize", true), equalTo(true))
    }

    @Test
    @Ignore("Tests XML FieldEditText clipboard/pastePlainText. In Compose mode, clipboard is handled differently. Requires UI test.")
    fun pasteHtmlAsPlainTextTest() {
        // TODO: Rewrite test for Compose clipboard handling
    }

    @Test
    fun `can switch two image occlusion note types 15579`() {
        // Ensure IO note type exists for the test
        if (col.notetypes.byName("Image Occlusion") == null) {
            return
        }

        val ioType1 = col.notetypes.byName("Image Occlusion")!!
        val ioType2 = getSecondImageOcclusionNoteType()

        // Ensure names are distinct
        if (ioType2.name == ioType1.name) {
            ioType2.name = "Image Occlusion 2"
            col.notetypes.save(ioType2)
        }

        val type1Name = ioType1.name
        val type2Name = ioType2.name

        val editor = getNoteEditorAddingNote(DECK_LIST)
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
    fun `edit note in filtered deck from reviewer - 15919`() {
        idleMainLooper()
        addDeck("A")
        val homeDeckId = addDeck("B", setAsSelected = true)
        val note = addBasicNote().updateCards { did = homeDeckId }
        moveToDynamicDeck(note)

        assertThat("home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("current deck", note.firstCard().did, not(equalTo(homeDeckId)))

        val editor = getNoteEditorEditingExistingBasicNote(note, REVIEWER)
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
    fun `editing card in filtered deck retains deck`() = runTest {
        val homeDeckId = addDeck("A")
        val note = addBasicNote().updateCards { did = homeDeckId }
        moveToDynamicDeck(note)

        assertThat("home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("current deck", note.firstCard().did, not(equalTo(homeDeckId)))

        val editor = getNoteEditorEditingExistingBasicNote(note, REVIEWER)
        idleMainLooper()

        editor.setFieldValueFromUi(0, "Hello")
        idleMainLooper()
        editor.saveNote()
        idleMainLooper()

        assertThat("after: home deck", note.firstCard().oDid, equalTo(homeDeckId))
        assertThat("after: current deck", note.firstCard().did, not(equalTo(homeDeckId)))
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
            col.notetypes.byName("Image Occlusion")!!.createClone()
        }
    }

    private fun getCopyNoteIntent(editor: NoteEditorFragment): Bundle {
        val editorShadow = shadowOf(editor.requireActivity())
        editor.copyNote()
        idleMainLooper()
        val intent = editorShadow.peekNextStartedActivityForResult().intent
        return intent.extras ?: Bundle()
    }

    private fun Spinner.getItemIndex(toFind: Any): Int? {
        for (i in 0 until count) {
            if (this.getItemAtPosition(i) != toFind) continue
            return i
        }
        return null
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
            REVIEWER -> NoteEditorLauncher.AddNoteFromReviewer().toBundle()
            DECK_LIST -> NoteEditorLauncher.AddNote().toBundle()
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
            REVIEWER -> NoteEditorLauncher.EditCard(n.firstCard().id, DEFAULT).toBundle()
            DECK_LIST -> NoteEditorLauncher.AddNote().toBundle()
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
            val noteEditor = getNoteEditorAddingNote(REVIEWER)
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
