// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Enno Hermann <enno.hermann@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ReverseDirection
import com.ichi2.anki.model.SortType

/**
 * Allows a user to set the [SortType] and [ReverseDirection]
 */
class CardBrowserOrderDialog : AnalyticsDialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val items = resources.getStringArray(R.array.card_browser_order_labels)
        // Set sort order arrow
        for (i in items.indices) {
            if (i != CARD_ORDER_NONE && i == viewModel.order.cardBrowserLabelIndex) {
                if (viewModel.isSortDescendingValue) {
                    items[i] = items[i].toString() + " (\u25b2)"
                } else {
                    items[i] = items[i].toString() + " (\u25bc)"
                }
            }
        }

        return AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.card_browser_change_display_order_title)
            .setSingleChoiceItems(items, viewModel.order.cardBrowserLabelIndex, orderSingleChoiceDialogListener)
            .create()
    }

    companion object {
        private var orderSingleChoiceDialogListener: DialogInterface.OnClickListener? = null

        // SortType.NO_SORTING.cardBrowserLabelIndex
        private const val CARD_ORDER_NONE = 0

        fun newInstance(orderSingleChoiceDialogListener: DialogInterface.OnClickListener): CardBrowserOrderDialog {
            this.orderSingleChoiceDialogListener = orderSingleChoiceDialogListener
            return CardBrowserOrderDialog()
        }
    }
}
