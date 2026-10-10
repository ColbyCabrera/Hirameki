/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.customstudy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.compose.TagsDialogContent
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import java.util.ArrayList

/**
 * The message shown when the tag selection limit is reached.
 *
 * The backend text is a long explanation rather than a one-line error, so we keep only its first
 * sentence. Shown with a long-duration toast, because a user who just hit a limit needs time to
 * read it.
 */
internal fun maxTagsMessage(): String {
    val collapsedWhitespace = TR.errors100TagsMax().replace(Regex("\\s+"), " ")
    val firstSentenceEnd = collapsedWhitespace.indexOf('.')
    return if (firstSentenceEnd < 0) {
        // the backend text was changed, so show it as-is rather than showing nothing useful
        collapsedWhitespace
    } else {
        collapsedWhitespace.substring(0..firstSentenceEnd)
    }
}

class CustomStudyTagsDialogFragment : DialogFragment() {
    private val viewModel: CustomStudyTagsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val cardsAmount = requireArguments().getInt(ARG_CARDS_AMOUNT, 100)
        val cramKind = requireArguments().getString(ARG_CRAM_KIND) ?: CramKind.CRAM_KIND_NEW.name

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val tagsState = viewModel.tagsState.collectAsStateWithLifecycle().value

                    TagsDialogContent(
                        onDismissRequest = { dismiss() },
                        onConfirm = { checkedTags, _ ->
                            setFragmentResult(
                                REQUEST_KEY,
                                Bundle().apply {
                                    putStringArrayList(EXTRA_SELECTED_TAGS, ArrayList(checkedTags))
                                    putInt(EXTRA_CARDS_AMOUNT, cardsAmount)
                                    putString(EXTRA_CRAM_KIND, cramKind)
                                },
                            )
                            dismiss()
                        },
                        allTags = tagsState,
                        initialSelection = emptySet(),
                        title = stringResource(R.string.studyoptions_limit_select_tags),
                        confirmButtonText = stringResource(R.string.dialog_ok),
                        maxSelection = MAX_TAGS_SELECTION,
                        onMaxSelectionReached = {
                            // `context` rather than `requireContext()`: this can fire as the
                            // dialog is being dismissed, when the fragment is already detached.
                            context?.let { showThemedToast(it, maxTagsMessage(), shortLength = false) }
                        },
                        onAddTag = null,
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "CustomStudyTagsDialogFragment"
        const val REQUEST_KEY = "CustomStudyTagsDialog_result"
        const val EXTRA_SELECTED_TAGS = "CustomStudyTagsDialog_selected_tags"
        const val EXTRA_CARDS_AMOUNT = "CustomStudyTagsDialog_cards_amount"
        const val EXTRA_CRAM_KIND = "CustomStudyTagsDialog_cram_kind"

        const val ARG_DECK_ID = "arg_deck_id"
        private const val ARG_CARDS_AMOUNT = "arg_cards_amount"
        private const val ARG_CRAM_KIND = "arg_cram_kind"

        private const val MAX_TAGS_SELECTION = 100

        fun show(
            fragmentManager: FragmentManager,
            deckId: DeckId,
            cardsAmount: Int,
            cramKind: CramKind,
        ) {
            val dialog =
                CustomStudyTagsDialogFragment().apply {
                    arguments =
                        Bundle().apply {
                            putLong(ARG_DECK_ID, deckId)
                            putInt(ARG_CARDS_AMOUNT, cardsAmount)
                            putString(ARG_CRAM_KIND, cramKind.name)
                        }
                }
            dialog.show(fragmentManager, TAG)
        }
    }
}
