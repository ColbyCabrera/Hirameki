// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.R
import com.ichi2.utils.create
import com.ichi2.utils.customListAdapter
import com.ichi2.utils.negativeButton
import com.ichi2.utils.title

/**
 * Dialog fragment used to show the fields that the user can insert in the card editor. This
 * fragment can notify other fragments from the same activity about an inserted field.
 *
 * @see [CardTemplateEditor.CardTemplateFragment]
 */
class InsertFieldDialog : DialogFragment() {
    private lateinit var fieldList: List<String>

    /**
     * A dialog for inserting field in card template editor
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreate(savedInstanceState)
        fieldList = requireArguments().getStringArrayList(KEY_FIELD_ITEMS)!!
        val adapter: RecyclerView.Adapter<*> =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ): RecyclerView.ViewHolder {
                    val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
                    return object : RecyclerView.ViewHolder(root) {}
                }

                override fun onBindViewHolder(
                    holder: RecyclerView.ViewHolder,
                    position: Int,
                ) {
                    val textView = holder.itemView as TextView
                    textView.text = fieldList[position]
                    textView.setOnClickListener { selectFieldAndClose(textView) }
                }

                override fun getItemCount(): Int = fieldList.size
            }
        return MaterialAlertDialogBuilder(requireContext()).create {
            title(R.string.card_template_editor_select_field)
            negativeButton(R.string.dialog_cancel)
            customListAdapter(adapter)
        }
    }

    private fun selectFieldAndClose(textView: TextView) {
        parentFragmentManager.setFragmentResult(
            REQUEST_FIELD_INSERT,
            Bundle().apply { putString(KEY_INSERTED_FIELD, textView.text.toString()) },
        )
        dismiss()
    }

    companion object {
        /**
         * Other fragments sharing the activity can use this with
         * [androidx.fragment.app.FragmentManager.setFragmentResultListener] to get a result back.
         */
        const val REQUEST_FIELD_INSERT = "request_field_insert"

        /**
         * This fragment requires that a list of fields names to be passed in.
         */
        const val KEY_INSERTED_FIELD = "key_inserted_field"
        private const val KEY_FIELD_ITEMS = "key_field_items"

        fun newInstance(fieldItems: List<String>): InsertFieldDialog =
            InsertFieldDialog().apply {
                arguments = Bundle().apply { putStringArrayList(KEY_FIELD_ITEMS, ArrayList(fieldItems)) }
            }
    }
}
