// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences.reviewer

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.ActionMenuView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.sharedPrefs
import kotlinx.coroutines.launch

class ReviewerMenuSettingsFragment :
    Fragment(R.layout.preferences_reviewer_menu),
    OnClearViewListener<ReviewerMenuSettingsRecyclerItem>,
    ActionMenuView.OnMenuItemClickListener {
    private lateinit var repository: ReviewerMenuRepository

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        repository = ReviewerMenuRepository(sharedPrefs())
        setupRecyclerView(view)
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<ReviewerMenuView>(R.id.reviewer_menu_view).apply {
            setOnMenuItemClickListener(this@ReviewerMenuSettingsFragment)
        }
    }

    private fun setupRecyclerView(view: View) {
        val menuItems = repository.getActionsByMenuDisplayTypes()

        fun section(displayType: MenuDisplayType): List<ReviewerMenuSettingsRecyclerItem> =
            listOf(ReviewerMenuSettingsRecyclerItem.DisplayType(displayType)) +
                menuItems.getValue(displayType).map { ReviewerMenuSettingsRecyclerItem.Action(it) }

        val recyclerViewItems = MenuDisplayType.entries.flatMap { section(it) }

        val callback = ReviewerMenuSettingsTouchHelperCallback(recyclerViewItems)
        callback.setOnClearViewListener(this)
        val itemTouchHelper = ItemTouchHelper(callback)

        val adapter =
            ReviewerMenuSettingsAdapter(recyclerViewItems).apply {
                setOnDragHandleTouchedListener { viewHolder ->
                    itemTouchHelper.startDrag(viewHolder)
                }
            }

        view.findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    override fun onClearView(items: List<ReviewerMenuSettingsRecyclerItem>) {
        fun getIndex(type: MenuDisplayType): Int =
            items.indexOfFirst {
                it is ReviewerMenuSettingsRecyclerItem.DisplayType && it.menuDisplayType == type
            }

        fun getSubList(
            fromIndex: Int,
            toIndex: Int,
        ): List<ViewerAction> =
            items.subList(fromIndex, toIndex).mapNotNull {
                (it as? ReviewerMenuSettingsRecyclerItem.Action)?.viewerAction
            }

        val menuOnlyItemsIndex = getIndex(MenuDisplayType.MENU_ONLY)
        val disabledItemsIndex = getIndex(MenuDisplayType.DISABLED)

        val alwaysShowActions = getSubList(1, menuOnlyItemsIndex)
        val menuOnlyActions = getSubList(menuOnlyItemsIndex, disabledItemsIndex)
        val disabledActions = getSubList(disabledItemsIndex, items.lastIndex)

        repository.setDisplayTypeActions(
            alwaysShowActions = alwaysShowActions,
            menuOnlyActions = menuOnlyActions,
            disabledActions = disabledActions,
        )

        lifecycleScope.launch {
            val menu = requireView().findViewById<ReviewerMenuView>(R.id.reviewer_menu_view)
            menu.clear()
            menu.addActions(alwaysShowActions, menuOnlyActions)
            menu.setFlagTitles()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val action = ViewerAction.fromId(item.itemId)
        if (action.isSubMenu()) return false

        item.title?.let { showSnackbar(it, Snackbar.LENGTH_SHORT) }
        return true
    }
}
