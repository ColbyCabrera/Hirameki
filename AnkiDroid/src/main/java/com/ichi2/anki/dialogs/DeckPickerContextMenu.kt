// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.libanki.DeckId
import com.ichi2.utils.title

class DeckPickerContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        require(requireArguments().containsKey(ARG_DECK_ID)) { "Missing argument deck id" }
        require(requireArguments().containsKey(ARG_DECK_NAME)) { "Missing argument deck name" }
        require(requireArguments().containsKey(ARG_DECK_IS_DYN)) { "Missing argument deck is dynamic" }
        require(requireArguments().containsKey(ARG_DECK_HAS_BURIED_IN_DECK)) { "Missing argument deck has buried" }
        val options = createOptionsList()
        return AlertDialog
            .Builder(requireActivity())
            .title(text = requireArguments().getString(ARG_DECK_NAME))
            .setItems(
                options.map { resources.getString(it.optionName) }.toTypedArray(),
            ) { _, index: Int ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_CONTEXT_MENU,
                    Bundle().apply {
                        putLong(CONTEXT_MENU_DECK_ID, requireArguments().getLong(ARG_DECK_ID))
                        putSerializable(CONTEXT_MENU_DECK_OPTION, options[index])
                    },
                )
            }.create()
    }

    private fun createOptionsList(): List<DeckPickerContextMenuOption> =
        DeckPickerMenuContentProvider.createOptionsList(
            requireArguments().getBoolean(ARG_DECK_IS_DYN),
            requireArguments().getBoolean(ARG_DECK_HAS_BURIED_IN_DECK),
        )

    enum class DeckPickerContextMenuOption(
        @StringRes val optionName: Int,
    ) {
        RENAME_DECK(R.string.rename_deck),
        DECK_OPTIONS(R.string.menu__deck_options),
        CUSTOM_STUDY(R.string.custom_study),
        DELETE_DECK(R.string.contextmenu_deckpicker_delete_deck),
        EXPORT_DECK(R.string.export_deck),
        UNBURY(R.string.unbury),
        CUSTOM_STUDY_REBUILD(R.string.rebuild_cram_label),
        CUSTOM_STUDY_EMPTY(R.string.empty_cram_label),
        CREATE_SUBDECK(R.string.create_subdeck),
        CREATE_SHORTCUT(R.string.create_shortcut),
        BROWSE_CARDS(R.string.browse_cards),
        EDIT_DESCRIPTION(R.string.edit_deck_description),
        ADD_CARD(R.string.menu_add),
        SCHEDULE_REMINDERS(R.string.schedule_reminders_do_not_translate),
    }

    companion object {
        const val REQUEST_KEY_CONTEXT_MENU = "request_key_context_menu"
        const val CONTEXT_MENU_DECK_OPTION = "context_menu_deck_option"
        const val CONTEXT_MENU_DECK_ID = "context_menu_deck_id"

        @VisibleForTesting
        const val ARG_DECK_ID = "arg_deck_id"

        @VisibleForTesting
        const val ARG_DECK_NAME = "arg_deck_name"

        @VisibleForTesting
        const val ARG_DECK_IS_DYN = "arg_deck_is_dyn"

        @VisibleForTesting
        const val ARG_DECK_HAS_BURIED_IN_DECK = "arg_deck_has_buried_in_deck"

        fun newInstance(
            id: DeckId,
            name: String,
            isDynamic: Boolean,
            hasBuriedInDeck: Boolean,
        ): DeckPickerContextMenu =
            DeckPickerContextMenu().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_DECK_ID, id)
                        putString(ARG_DECK_NAME, name)
                        putBoolean(ARG_DECK_IS_DYN, isDynamic)
                        putBoolean(ARG_DECK_HAS_BURIED_IN_DECK, hasBuriedInDeck)
                    }
            }
    }
}
