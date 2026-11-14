// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleListAdapter.TextViewHolder
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.TypedFilter
import com.ichi2.utils.cancelable
import com.ichi2.utils.customView
import com.ichi2.utils.show
import java.util.Locale

/**
 * Shows a list of [Locale] from which the user can set one as the keyboard hint for that field.
 * Currently supported only by Gboard.
 * @see LanguageHintService
 */
class LocaleSelectionDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val localeAdapter =
            LocaleListAdapter(
                Locale.getAvailableLocales() + IPALanguage,
                ::sendSelectionResult,
            )
        val dialogView = layoutInflater.inflate(R.layout.locale_selection_dialog, null)
        dialogView
            .findViewById<RecyclerView>(R.id.locale_dialog_selection_list)
            .adapter = localeAdapter
        dialogView
            .findViewById<Toolbar>(R.id.locale_dialog_selection_toolbar)
            .setupMenuWith(localeAdapter)
        return MaterialAlertDialogBuilder(requireContext()).show {
            cancelable(true)
            customView(dialogView)
        }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        dialog.window?.let { resizeWhenSoftInputShown(it) }
    }

    private fun Toolbar.setupMenuWith(adapter: LocaleListAdapter) {
        inflateMenu(R.menu.locale_dialog_search_bar)
        setNavigationOnClickListener { sendSelectionResult() }
        (menu.findItem(R.id.locale_dialog_action_search).actionView as AccessibleSearchView).apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean = false

                    override fun onQueryTextChange(newText: String): Boolean {
                        adapter.filter.filter(newText)
                        return false
                    }
                },
            )
        }
    }

    private fun sendSelectionResult(locale: Locale? = null) {
        parentFragmentManager.setFragmentResult(
            REQUEST_HINT_LOCALE_SELECTION,
            Bundle().apply { putSerializable(KEY_SELECTED_LOCALE, locale) },
        )
    }

    private inner class LocaleListAdapter(
        private val locales: Array<Locale>,
        private val onLocaleSelected: (Locale) -> Unit,
    ) : RecyclerView.Adapter<TextViewHolder>(),
        Filterable {
        private val filteredLocales: MutableList<Locale> = locales.toMutableList()

        inner class TextViewHolder(
            val textView: TextView,
        ) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = TextViewHolder(
            layoutInflater
                .inflate(R.layout.locale_dialog_fragment_textview, parent, false) as TextView,
        )

        override fun onBindViewHolder(
            holder: TextViewHolder,
            position: Int,
        ) {
            val locale = filteredLocales[position]
            holder.textView.text = locale.displayName
            holder.textView.setOnClickListener { onLocaleSelected(locale) }
        }

        override fun getItemCount(): Int = filteredLocales.size

        override fun getFilter(): Filter {
            return object : TypedFilter<Locale>({ locales.toList() }) {
                override fun filterResults(
                    constraint: CharSequence,
                    items: List<Locale>,
                ): List<Locale> {
                    val normalisedConstraint = constraint.toString().lowercase(Locale.getDefault())
                    return items.filter {
                        it.displayName.lowercase(Locale.getDefault()).contains(normalisedConstraint)
                    }
                }

                override fun publishResults(
                    constraint: CharSequence?,
                    results: List<Locale>,
                ) {
                    filteredLocales.clear()
                    filteredLocales.addAll(results)
                    notifyDataSetChanged()
                }
            }
        }
    }

    companion object {
        const val REQUEST_HINT_LOCALE_SELECTION = "request_hint_locale_selection"
        const val KEY_SELECTED_LOCALE = "key_selected_locale"

        /**
         * Language identifier for International Phonetic Alphabet. This isn't available from [Locale.getAvailableLocales], but
         * GBoard seems to understand this as a language code.
         *
         * See issue #13883
         * See https://en.wikipedia.org/wiki/International_Phonetic_Alphabet#IETF_language_tags
         */
        private val IPALanguage = Locale.Builder().setLanguageTag("und-fonipa").build()
    }
}
