/*
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.Destination

/**
 * Defines various configurations for opening the NoteEditor with specific data or actions.
 */
sealed interface NoteEditorLauncher : Destination {
    override fun toIntent(context: Context): Intent = toIntent(context, action = null)

    /**
     * Generates an intent to open the NoteEditor activity with the configured parameters
     *
     * @param context The context from which the intent is launched.
     * @param action Optional action string for the intent.
     * @return Intent configured to launch the NoteEditor activity.
     */
    fun toIntent(
        context: Context,
        action: String? = null,
    ) = Intent(context, NoteEditorActivity::class.java).apply {
        putExtras(toBundle())
        action?.let { this.action = it }
    }

    /**
     * Converts the configuration into a Bundle to pass arguments to the NoteEditor.
     *
     * @return Bundle containing arguments specific to this configuration.
     */
    fun toBundle(): Bundle

    /**
     * Companion object for [NoteEditorLauncher], providing factory methods for creating launchers.
     */
    companion object {
        /**
         * Creates a [NoteEditorLauncher] from an [Intent].
         *
         * This function is responsible for parsing the incoming intent and determining the correct
         * [NoteEditorLauncher] to use. It handles various ways the NoteEditor can be launched,
         * including from different parts of the app (like DeckPicker, CardBrowser) and from external
         * intents.
         */
        fun fromIntent(intent: Intent): NoteEditorLauncher {
            // Case 1: The intent has FRAGMENT_NAME_EXTRA - handle this as a special case.
            if (intent.hasExtra(NoteEditorActivity.FRAGMENT_NAME_EXTRA)) {
                val fragmentName = intent.getStringExtra(NoteEditorActivity.FRAGMENT_NAME_EXTRA)
                if (fragmentName == "com.ichi2.anki.NoteEditorFragment" || fragmentName == NoteEditorActivity::class.java.name) {
                    val args = intent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
                    if (args != null) {
                        return PassArguments(args)
                    }
                }
                return AddNote()
            }

            // Case 2: No FRAGMENT_NAME_EXTRA - check for arguments in FRAGMENT_ARGS_EXTRA.
            val directArgs = intent.getBundleExtra(NoteEditorActivity.FRAGMENT_ARGS_EXTRA)
            if (directArgs != null) {
                return PassArguments(directArgs)
            }

            // Case 3: The entire `extras` bundle is the arguments. This is a common case for external intents.
            intent.extras?.let { bundle ->
                if (!bundle.isEmpty) {
                    return PassArguments(bundle)
                }
            }

            // Fallback for all other cases (no args, empty bundles, etc.)
            return AddNote()
        }
    }

    /**
     * Represents opening the NoteEditor with an image occlusion.
     * @property imageUri The URI of the image to occlude.
     */
    data class ImageOcclusion(
        val imageUri: Uri?,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.IMG_OCCLUSION.value)
            putParcelable(NoteEditorActivity.EXTRA_IMG_OCCLUSION, imageUri)
        }
    }

    /**
     * Represents opening the NoteEditor with custom arguments.
     * @property arguments The bundle of arguments to pass.
     */
    data class PassArguments(
        val arguments: Bundle,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = arguments
    }

    /**
     * Represents adding a note to the NoteEditor within a specific deck (Optional).
     * @property deckId The ID of the deck where the note should be added.
     */
    data class AddNote(
        val deckId: DeckId? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
            deckId?.let { putLong(NoteEditorActivity.EXTRA_DID, it) }
        }
    }

    /**
     * Represents adding a note to the NoteEditor from the card browser.
     * @property viewModel The view model containing data from the card browser.
     */
    data class AddNoteFromCardBrowser(
        val viewModel: CardBrowserViewModel,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle {
            val fragmentArgs = Bundle().apply {
                putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                putString(NoteEditorActivity.EXTRA_TEXT_FROM_SEARCH_VIEW, viewModel.searchTerms)
                putBoolean(NoteEditorActivity.IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
                viewModel.lastDeckId?.let { if (it > 0) putLong(NoteEditorActivity.EXTRA_DID, it) }
            }
            return Bundle().apply {
                putBundle(NoteEditorActivity.FRAGMENT_ARGS_EXTRA, fragmentArgs)
            }
        }
    }

    /**
     * Represents adding a note to the NoteEditor from the reviewer.
     * @property animation The animation direction to use when transitioning.
     */
    data class AddNoteFromReviewer(
        val animation: ActivityTransitionAnimation.Direction? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle {
            val fragmentArgs = Bundle().apply {
                putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.REVIEWER_ADD.value)
                animation?.let {
                    putParcelable(AnkiActivity.FINISH_ANIMATION_EXTRA, it as Parcelable)
                }
            }

            return Bundle().apply {
                putBundle(NoteEditorActivity.FRAGMENT_ARGS_EXTRA, fragmentArgs)
            }
        }
    }

    /**
     * Allows to move from Instant note editor to standard note editor while keeping the text content
     *
     * @property sharedText The shared text content for the instant note.
     */
    data class AddInstantNote(
        val sharedText: String,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.INSTANT_NOTE_EDITOR.value)
            putString(Intent.EXTRA_TEXT, sharedText)
        }
    }

    /**
     * Represents editing a card in the NoteEditor.
     * @property cardId The ID of the card to edit.
     * @property animation The animation direction.
     */
    data class EditCard(
        val cardId: CardId,
        val animation: ActivityTransitionAnimation.Direction,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
            putLong(NoteEditorActivity.EXTRA_CARD_ID, cardId)
            putParcelable(AnkiActivity.FINISH_ANIMATION_EXTRA, animation as Parcelable)
            putBoolean(NoteEditorActivity.IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
        }
    }

    /**
     * Represents editing a note in the NoteEditor from the previewer.
     * @property cardId The ID of the card associated with the note to edit.
     */
    data class EditNoteFromPreviewer(
        val cardId: CardId,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.PREVIEWER_EDIT.value)
            putLong(NoteEditorActivity.EXTRA_EDIT_FROM_CARD_ID, cardId)
        }
    }

    /**
     * Represents copying a note to the NoteEditor.
     * @property deckId The ID of the deck where the note should be copied.
     * @property fieldsText The text content of the fields to copy.
     * @property tags Optional list of tags to assign to the copied note.
     */
    data class CopyNote(
        val deckId: DeckId,
        val fieldsText: String,
        val tags: List<String>? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = Bundle().apply {
            putInt(NoteEditorActivity.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR.value)
            putLong(NoteEditorActivity.EXTRA_DID, deckId)
            putString(NoteEditorActivity.EXTRA_CONTENTS, fieldsText)
            tags?.let { putStringArray(NoteEditorActivity.EXTRA_TAGS, it.toTypedArray()) }
        }
    }
}
